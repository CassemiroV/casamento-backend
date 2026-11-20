package br.com.casamento.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;

import br.com.casamento.dtos.request.CreatePaymentRequest;
import br.com.casamento.dtos.response.PaymentResponse;
import br.com.casamento.dtos.response.PaymentStatusResponse;
import br.com.casamento.entities.Gift;
import br.com.casamento.entities.Payment;
import br.com.casamento.enums.PaymentStatus;
import br.com.casamento.repositories.GiftRepository;
import br.com.casamento.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final GiftRepository giftRepository;
    private final MercadoPagoService mercadoPagoService;
    private final TurnstileValidationService turnstileValidationService;

    // As falhas HTTP esperadas preservam o histórico da tentativa e as conciliações já concluídas.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PaymentResponse processPayment(CreatePaymentRequest request) {
        if (!turnstileValidationService.validateTurnstileResponse(request.captchaToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verificação de segurança falhou. Tente novamente.");
        }

        Gift gift = lockGift(request.giftId());
        if (Boolean.TRUE.equals(gift.getPurchased())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este presente já foi comprado");
        }

        boolean pendingCheckout = false;
        for (Payment pending : paymentRepository.findAllByGift_IdAndStatus(gift.getId(), PaymentStatus.PENDING)) {
            if (pending.getGatewayOrderId() != null) {
                refreshOrder(pending, gift);
                pendingCheckout |= pending.getStatus() == PaymentStatus.PENDING;
            }
        }

        if (Boolean.TRUE.equals(gift.getPurchased())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este presente já foi comprado");
        }
        if (pendingCheckout) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um pagamento em aberto para este presente");
        }

        Payment payment = new Payment(
            gift, request.guestName(), request.guestEmail(), gift.getPrice(),
            null, null, null, PaymentStatus.PENDING
        );
        if (StringUtils.hasText(request.message())) {
            payment.setMessage(request.message());
        }
        paymentRepository.save(payment);

        try {
            Order order = mercadoPagoService.createOrder(payment);
            if (StringUtils.hasText(order.getId())) {
                payment.setGatewayOrderId(order.getId());
                payment.setGatewayStatus(order.getStatus());
                payment.setGatewayStatusDetail(order.getStatusDetail());
            }
            if (payment.getGatewayOrderId() == null || !StringUtils.hasText(order.getCheckoutUrl())) {
                log.warn("Mercado Pago retornou uma ordem sem ID ou URL de checkout para o pagamento {}", payment.getId());
                throw checkoutFailed(payment);
            }
            payment.setCheckoutUrl(order.getCheckoutUrl());
            return new PaymentResponse(payment.getId(), payment.getCheckoutUrl());
        } catch (MPApiException e) {
            log.warn("Mercado Pago retornou HTTP {} ao criar o pagamento {}", e.getStatusCode(), payment.getId());
        } catch (MPException e) {
            log.warn("Falha de comunicação ao criar o pagamento {}", payment.getId());
        }
        throw checkoutFailed(payment);
    }

    @Transactional
    public PaymentStatusResponse getPaymentStatus(UUID paymentId) {
        UUID giftId = paymentRepository.findGiftIdByPaymentId(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento não encontrado"));
        Gift gift = lockGift(giftId);
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagamento não encontrado"));

        Instant checkedAt = payment.getGatewayCheckedAt();
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getGatewayOrderId() != null
                && (checkedAt == null || checkedAt.isBefore(Instant.now().minusSeconds(10)))) {
            refreshOrder(payment, gift);
        }

        return new PaymentStatusResponse(
            payment.getId(), payment.getStatus(), payment.getGatewayStatus(), payment.getGatewayStatusDetail()
        );
    }

    @Transactional
    public void processOrderNotification(String orderId) {
        var giftId = paymentRepository.findGiftIdByGatewayOrderId(orderId);
        if (giftId.isPresent()) {
            Gift gift = lockGift(giftId.get());
            paymentRepository.findByGatewayOrderId(orderId).ifPresent(payment -> refreshOrder(payment, gift));
            return;
        }

        // Uma criação sem resposta pode ter deixado apenas a referência local registrada.
        Order order = mercadoPagoService.getOrder(orderId);
        String reference = order.getExternalReference();
        if (!StringUtils.hasText(reference)) {
            return;
        }
        paymentRepository.findGiftIdByExternalReference(reference).ifPresent(id -> {
            Gift gift = lockGift(id);
            paymentRepository.findByExternalReference(reference).ifPresent(payment -> {
                if (orderId.equals(payment.getGatewayOrderId())) {
                    refreshOrder(payment, gift);
                } else if (payment.getGatewayOrderId() == null) {
                    applyOrder(payment, gift, orderId, order);
                }
            });
        });
    }

    private Gift lockGift(UUID giftId) {
        return giftRepository.findByIdForUpdate(giftId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Presente não encontrado"));
    }

    private ResponseStatusException checkoutFailed(Payment payment) {
        payment.setStatus(PaymentStatus.FAILED);
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível criar o checkout no Mercado Pago");
    }

    private void refreshOrder(Payment payment, Gift gift) {
        String orderId = payment.getGatewayOrderId();
        applyOrder(payment, gift, orderId, mercadoPagoService.getOrder(orderId));
    }

    private void applyOrder(Payment payment, Gift gift, String orderId, Order order) {
        if (!orderId.equals(order.getId())
                || !Objects.equals(payment.getExternalReference(), order.getExternalReference())
                || !"BRL".equals(order.getCurrency())
                || !hasAmount(order.getTotalAmount(), payment.getAmount())) {
            throw invalidOrder(payment, orderId);
        }

        String status = order.getStatus();
        String detail = order.getStatusDetail();
        PaymentStatus previous = payment.getStatus();
        PaymentStatus current = switch (status == null ? "" : status) {
            case "processed" -> switch (detail == null ? "" : detail) {
                case "accredited", "partially_refunded" -> {
                    if ("accredited".equals(detail) && !hasAmount(order.getTotalPaidAmount(), payment.getAmount())) {
                        throw invalidOrder(payment, orderId);
                    }
                    yield PaymentStatus.PAID;
                }
                default -> previous;
            };
            case "canceled", "refunded" -> PaymentStatus.CANCELLED;
            case "failed" -> PaymentStatus.FAILED;
            case "charged_back" -> "reimbursed".equals(detail) ? PaymentStatus.CANCELLED : previous;
            default -> previous;
        };

        payment.setGatewayOrderId(orderId);
        payment.setGatewayStatus(status);
        payment.setGatewayStatusDetail(detail);
        payment.setGatewayCheckedAt(Instant.now());
        payment.setStatus(current);

        if (current == PaymentStatus.PAID) {
            gift.setPurchased(true);
        } else if (previous == PaymentStatus.PAID) {
            gift.setPurchased(paymentRepository.existsByGift_IdAndStatus(gift.getId(), PaymentStatus.PAID));
        }
    }

    private ResponseStatusException invalidOrder(Payment payment, String orderId) {
        log.warn("Dados da ordem {} divergentes do pagamento {}", orderId, payment.getId());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "A ordem do Mercado Pago diverge do pagamento registrado");
    }

    private boolean hasAmount(String amount, BigDecimal expected) {
        if (amount == null) {
            return false;
        }
        try {
            return new BigDecimal(amount).compareTo(expected) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

package br.com.casamento.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;

import br.com.casamento.dtos.request.CreatePaymentRequest;
import br.com.casamento.dtos.response.PaymentResponse;
import br.com.casamento.entities.Gift;
import br.com.casamento.entities.Payment;
import br.com.casamento.enums.PaymentStatus;
import br.com.casamento.repositories.GiftRepository;
import br.com.casamento.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final GiftRepository giftRepository;
    private final MercadoPagoService mercadoPagoService;

    @Transactional
    public PaymentResponse processPayment(CreatePaymentRequest request) {

        // 1. Busca o presente
        Gift gift = giftRepository.findById(request.giftId())
            .orElseThrow(() -> new IllegalArgumentException("Presente não encontrado"));

        // 2. Verifica se já foi comprado
        if (Boolean.TRUE.equals(gift.getPurchased())) {
            throw new IllegalStateException("Este presente já foi comprado");
        }

        // 3. Cria o Payment local com status PENDING
        Payment payment = new Payment(
            gift,
            request.guestName(),
            request.guestEmail(),
            gift.getPrice(),          // ← preço vindo do BANCO, nunca do frontend
            null,                      // gatewayOrderId (preenchido depois)
            null,                      // externalReference (preenchido pelo @PrePersist)
            null,                      // checkoutUrl (preenchido depois)
            PaymentStatus.PENDING
        );

        // 4. Salva para gerar o id e o externalReference
        payment = paymentRepository.save(payment);

        // 5. Chama o Mercado Pago para criar a Order
        try {
            Order order = mercadoPagoService.createOrder(payment);

            // 6. Atualiza o Payment com os dados do MP
            payment.setGatewayOrderId(order.getId());
            payment.setCheckoutUrl(order.getCheckoutUrl());
            payment = paymentRepository.save(payment);

        } catch (MPException | MPApiException e) {
            // Se o MP falhar, marca o Payment como FAILED e propaga o erro
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new RuntimeException("Erro ao criar pagamento no Mercado Pago", e);
        }

        // 7. Retorna o DTO
        return new PaymentResponse(payment.getId(), payment.getCheckoutUrl());
    }
}
package br.com.casamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;

import br.com.casamento.entities.Gift;
import br.com.casamento.entities.Payment;
import br.com.casamento.enums.GiftGroup;
import br.com.casamento.enums.PaymentStatus;
import br.com.casamento.repositories.GiftRepository;
import br.com.casamento.repositories.PaymentRepository;

@SpringBootTest(properties = "mercadopago.webhook.secret=webhook-test-secret")
@AutoConfigureMockMvc
class PaymentFlowTests {

    private static final String WEBHOOK = "/payments/webhooks/mercadopago";
    private static final String ORDER_ID = "ORDTESTPAYMENT01";
    private static final String REQUEST_ID = "request-test-id";
    private static final String CHECKOUT_URL = "https://www.mercadopago.com.br/checkout/test";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private GiftRepository giftRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private br.com.casamento.services.MercadoPagoService mercadoPago;

    @MockitoBean
    private TurnstileValidationService turnstile;

    private Gift gift;

    @BeforeEach
    void createGift() {
        Gift newGift = new Gift();
        newGift.setTitle("Presente de teste");
        newGift.setDescription("Presente para testar a conciliação");
        newGift.setGiftGroup(GiftGroup.CASA);
        newGift.setPrice(new BigDecimal("50.00"));
        gift = giftRepository.save(newGift);
        when(turnstile.validateTurnstileResponse("test-token")).thenReturn(true);
    }

    @Test
    void signedWebhookConfirmsAndRefundsAnOrderDespiteRepeatedNotifications() throws Exception {
        Payment payment = pendingPayment(ORDER_ID);
        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(payment, ORDER_ID, "processed", "accredited", "50.00"));

        notifyOrder(ORDER_ID, signedHeader(ORDER_ID.toLowerCase(Locale.ROOT))).andExpect(status().isOk());
        notifyOrder(ORDER_ID, signedHeader(ORDER_ID)).andExpect(status().isOk());
        mvc.perform(get("/payments/{paymentId}", payment.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("PAID"));
        assertThat(giftRepository.findById(gift.getId()).orElseThrow().getPurchased()).isTrue();

        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(payment, ORDER_ID, "refunded", "refunded", "0.00"));
        notifyOrder(ORDER_ID, signedHeader(ORDER_ID)).andExpect(status().isOk());

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(giftRepository.findById(gift.getId()).orElseThrow().getPurchased()).isFalse();
    }

    @Test
    void rejectsSignatureForAnotherOrderWithoutCallingGateway() throws Exception {
        Payment payment = pendingPayment(ORDER_ID);

        notifyOrder(ORDER_ID, signedHeader("ORDANOTHERPAYMENT")).andExpect(status().isUnauthorized());

        verifyNoInteractions(mercadoPago);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void rejectsUnconfirmedAmountAndAllowsGatewayToRetry() throws Exception {
        Payment payment = pendingPayment(ORDER_ID);
        when(mercadoPago.getOrder(ORDER_ID))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY))
            .thenReturn(order(payment, ORDER_ID, "processed", "accredited", "49.00"))
            .thenReturn(order(payment, ORDER_ID, "processed", "accredited", "50.00"));

        String signature = signedHeader(ORDER_ID);
        notifyOrder(ORDER_ID, signature).andExpect(status().isBadGateway());
        notifyOrder(ORDER_ID, signature).andExpect(status().isBadGateway());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);

        notifyOrder(ORDER_ID, signature).andExpect(status().isOk());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void statusLookupReconcilesAnOlderPendingPayment() throws Exception {
        Payment payment = pendingPayment(ORDER_ID);
        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(payment, ORDER_ID, "failed", "high_risk", "0.00"));

        mvc.perform(get("/payments/{paymentId}", payment.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.gatewayStatusDetail").value("high_risk"));

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void webhookCanLinkAnOrderByItsExternalReference() throws Exception {
        Payment payment = pendingPayment(null);
        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(payment, ORDER_ID, "processed", "accredited", "50.00"));

        notifyOrder(ORDER_ID, signedHeader(ORDER_ID)).andExpect(status().isOk());

        Payment persisted = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(persisted.getGatewayOrderId()).isEqualTo(ORDER_ID);
        assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void checkoutFailurePersistsFailedAttempt() throws Exception {
        when(mercadoPago.createOrder(any())).thenThrow(new MPException("Connection failed"));

        createCheckout().andExpect(status().isBadGateway());

        assertThat(paymentRepository.findAllByGift_IdAndStatus(gift.getId(), PaymentStatus.FAILED)).hasSize(1);
    }

    @Test
    void checkoutRejectsSecondOpenOrderAndAllowsReplacementAfterExpiration() throws Exception {
        Payment pending = pendingPayment(ORDER_ID);
        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(pending, ORDER_ID, "created", "created", "0.00"));

        createCheckout().andExpect(status().isConflict());
        verify(mercadoPago).getOrder(ORDER_ID);

        when(mercadoPago.getOrder(ORDER_ID)).thenReturn(order(pending, ORDER_ID, "canceled", "expired", "0.00"));
        when(mercadoPago.createOrder(any())).thenAnswer(call ->
            order(call.getArgument(0), "ORDREPLACEMENT01", "created", "created", "0.00")
        );

        createCheckout()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));
        assertThat(paymentRepository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentRepository.findAllByGift_IdAndStatus(gift.getId(), PaymentStatus.PENDING)).hasSize(1);
    }

    private Payment pendingPayment(String orderId) {
        return paymentRepository.save(new Payment(
            gift, "Convidado", "teste@example.com", gift.getPrice(),
            orderId, null, orderId == null ? null : CHECKOUT_URL, PaymentStatus.PENDING
        ));
    }

    private Order order(Payment payment, String id, String status, String detail, String paidAmount) {
        Order order = org.mockito.Mockito.mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getExternalReference()).thenReturn(payment.getExternalReference());
        when(order.getCurrency()).thenReturn("BRL");
        when(order.getTotalAmount()).thenReturn("50.0");
        when(order.getTotalPaidAmount()).thenReturn(paidAmount);
        when(order.getStatus()).thenReturn(status);
        when(order.getStatusDetail()).thenReturn(detail);
        when(order.getCheckoutUrl()).thenReturn(CHECKOUT_URL);
        return order;
    }

    private ResultActions notifyOrder(String orderId, String signature) throws Exception {
        return mvc.perform(post(WEBHOOK)
            .queryParam("data.id", orderId)
            .queryParam("type", "order")
            .header("x-request-id", REQUEST_ID)
            .header("x-signature", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"action":"order.created","data":{"status":"created"}}
                """));
    }

    private ResultActions createCheckout() throws Exception {
        return mvc.perform(post("/payments/pay").contentType(MediaType.APPLICATION_JSON).content("""
            {"giftId":"%s","guestName":"Convidado","guestEmail":"teste@example.com","captchaToken":"test-token"}
            """.formatted(gift.getId())));
    }

    private String signedHeader(String orderId) throws Exception {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        String manifest = "id:" + orderId + ";request-id:" + REQUEST_ID + ";ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("webhook-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}

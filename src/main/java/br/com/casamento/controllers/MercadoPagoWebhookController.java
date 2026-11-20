package br.com.casamento.controllers;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;

import br.com.casamento.services.PaymentService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final PaymentService paymentService;

    @Value("${mercadopago.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/payments/webhooks/mercadopago")
    public void receive(
        @RequestParam("data.id") String orderId,
        @RequestParam(name = "type", defaultValue = "order") String type,
        @RequestHeader(name = "x-signature", required = false) String signature,
        @RequestHeader(name = "x-request-id", required = false) String requestId
    ) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook do Mercado Pago sem configuração");
        }
        if (!orderId.matches("[a-zA-Z0-9]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de ordem inválido");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida");
        }
        if (!hasValidSignature(signature, requestId, orderId)
                && !hasValidSignature(signature, requestId, orderId.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura do webhook inválida");
        }

        if ("order".equals(type) || "orders_v2".equals(type)) {
            paymentService.processOrderNotification(orderId.toUpperCase(Locale.ROOT));
        }
    }

    private boolean hasValidSignature(String signature, String requestId, String orderId) {
        try {
            WebhookSignatureValidator.validate(signature, requestId, orderId, webhookSecret);
            return true;
        } catch (MPInvalidWebhookSignatureException e) {
            return false;
        }
    }
}

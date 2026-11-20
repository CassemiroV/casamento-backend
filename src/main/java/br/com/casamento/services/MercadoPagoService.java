package br.com.casamento.services;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.order.OrderConfigRequest;
import com.mercadopago.client.order.OrderCreateRequest;
import com.mercadopago.client.order.OrderItemRequest;
import com.mercadopago.client.order.OrderOnlineConfig;
import com.mercadopago.client.order.OrderPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;

import br.com.casamento.entities.Payment;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MercadoPagoService {

    private final OrderClient orderClient = new OrderClient();

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Value("${mercadopago.checkout.return-url}")
    private String returnUrl;

    public Order createOrder(Payment payment) throws MPException, MPApiException {
        String amount = payment.getAmount().toPlainString();
        OrderItemRequest item = OrderItemRequest.builder()
            .title(payment.getGift().getTitle())
            .quantity(1)
            .unitPrice(amount)
            .build();

        var request = OrderCreateRequest.builder()
            .type("online")
            .processingMode("manual")
            .captureMode("automatic_async")
            .expirationTime("P1D")
            .totalAmount(amount)
            .externalReference(payment.getExternalReference())
            .payer(OrderPayerRequest.builder().email(payment.getGuestEmail()).build())
            .items(List.of(item));

        if (!returnUrl.isBlank()) {
            request.config(OrderConfigRequest.builder()
                .online(OrderOnlineConfig.builder()
                    .successUrl(returnUrl)
                    .failureUrl(returnUrl)
                    .pendingUrl(returnUrl)
                    .autoReturn("all")
                    .build())
                .build());
        }

        MPRequestOptions options = requestOptions()
            .customHeaders(Map.of("X-Idempotency-Key", payment.getId().toString()))
            .maxRetries(1)
            .build();

        return orderClient.create(request.build(), options);
    }

    public Order getOrder(String orderId) {
        try {
            return orderClient.get(orderId, requestOptions().maxRetries(0).build());
        } catch (MPApiException e) {
            log.warn("Mercado Pago retornou HTTP {} na consulta da ordem {}", e.getStatusCode(), orderId);
        } catch (MPException e) {
            log.warn("Falha de comunicação na consulta da ordem {}", orderId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar a ordem no Mercado Pago");
    }

    private MPRequestOptions.MPRequestOptionsBuilder requestOptions() {
        return MPRequestOptions.builder()
            .accessToken(accessToken)
            .connectionTimeout(2000)
            .connectionRequestTimeout(2000)
            .socketTimeout(3000);
    }
}

package br.com.casamento.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.order.OrderClient;
import com.mercadopago.client.order.OrderCreateRequest;
import com.mercadopago.client.order.OrderItemRequest;
import com.mercadopago.client.order.OrderPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.order.Order;

import br.com.casamento.entities.Payment;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MercadoPagoService {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    public Order createOrder(Payment payment) throws MPException, MPApiException {

        MercadoPagoConfig.setAccessToken(accessToken);

        OrderItemRequest item = OrderItemRequest.builder()
            .title(payment.getGift().getTitle())
            .quantity(1)
            .unitPrice(payment.getAmount().toString())
            .build();

        OrderCreateRequest request = OrderCreateRequest.builder()
            .type("online")
            .processingMode("manual")
            .totalAmount(payment.getAmount().toString())
            .externalReference(payment.getExternalReference())
            .payer(OrderPayerRequest.builder()
                .email(payment.getGuestEmail())
                .build())
            .items(List.of(item))
            .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Idempotency-Key", UUID.randomUUID().toString());
        MPRequestOptions requestOptions = MPRequestOptions.builder()
            .customHeaders(headers)
            .build();

        OrderClient client = new OrderClient();
        return client.create(request, requestOptions);
    }
}
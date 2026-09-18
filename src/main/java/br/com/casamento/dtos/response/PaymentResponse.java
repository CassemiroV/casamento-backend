package br.com.casamento.dtos.response;

import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    String checkoutUrl
) {
    
}

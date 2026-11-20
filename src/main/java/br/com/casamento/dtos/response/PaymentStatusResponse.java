package br.com.casamento.dtos.response;

import java.util.UUID;

import br.com.casamento.enums.PaymentStatus;

public record PaymentStatusResponse(
    UUID paymentId,
    PaymentStatus status,
    String gatewayStatus,
    String gatewayStatusDetail
) {
}

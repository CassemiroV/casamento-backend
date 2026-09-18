package br.com.casamento.dtos.request;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(@NotNull UUID giftId, @NotBlank @Size(max = 50) String guestName, @NotBlank @Email @Size(max = 100) String guestEmail) {
    
}

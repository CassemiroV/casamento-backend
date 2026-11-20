package br.com.casamento.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmationInviteRequest(
    @NotBlank @Size(max = 255) String guestName,
    @NotBlank @Email @Size(max = 255) String guestEmail,
    @NotBlank String captchaToken
) {
    
}

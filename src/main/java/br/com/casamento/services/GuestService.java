package br.com.casamento.services;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.digitalsanctuary.cf.turnstile.service.TurnstileValidationService;

import br.com.casamento.dtos.request.ConfirmationInviteRequest;
import br.com.casamento.entities.Guest;
import br.com.casamento.repositories.GuestRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GuestService {
    
    private final GuestRepository guestRepository;

    private final TurnstileValidationService turnstileValidationService;

    public void confirmInvitation(ConfirmationInviteRequest invite) {

        if (!turnstileValidationService.validateTurnstileResponse(invite.captchaToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verificação de segurança falhou. Tente novamente.");
        }

        Guest guest = new Guest();
        guest.setGuestName(invite.guestName());
        guest.setGuestEmail(invite.guestEmail());
        guest.setCreatedAt(LocalDateTime.now());
        guestRepository.save(guest);
    }
}

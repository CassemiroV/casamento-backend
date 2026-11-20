package br.com.casamento.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.casamento.dtos.request.ConfirmationInviteRequest;
import br.com.casamento.services.GuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://viniciuscassemiro.com.br",
    "https://viniciuscassemiro.com.br",
    "https://www.viniciuscassemiro.com.br"
})
@RestController
@RequestMapping ("/guests")
@RequiredArgsConstructor
public class GuestController {
    
    private final GuestService guestService;

    @PostMapping ("/confirm-invitation")
    public ResponseEntity<String> confirmInvitation(@RequestBody @Valid ConfirmationInviteRequest request) {
        guestService.confirmInvitation(request);
        return ResponseEntity.ok("Invitation confirmed successfully.");
    }

}

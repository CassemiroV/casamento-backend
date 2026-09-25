package br.com.casamento.controllers;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.casamento.dtos.request.CreatePaymentRequest;
import br.com.casamento.dtos.response.PaymentResponse;
import br.com.casamento.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174",
    "http://viniciuscassemiro.com.br",
    "https://viniciuscassemiro.com.br",
    "https://www.viniciuscassemiro.com.br"
})
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/pay") 
    public PaymentResponse processPayment(@Valid @RequestBody CreatePaymentRequest paymentRequest) {
        return paymentService.processPayment(paymentRequest);
    }
}

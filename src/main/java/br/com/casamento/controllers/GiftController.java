package br.com.casamento.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.casamento.dtos.response.GiftGroupResponse;
import br.com.casamento.entities.Gift;
import br.com.casamento.services.GiftService;
import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = {
    "http://localhost:5173",
    "http://localhost:5174"
})
@RestController
@RequestMapping ("/gifts")
@RequiredArgsConstructor
public class GiftController {
    
    private final GiftService giftService;

    @GetMapping
    public ResponseEntity<List<GiftGroupResponse>> listGrouped() {
        List<GiftGroupResponse> giftGroups = giftService.listGrouped();
        return ResponseEntity.ok(giftGroups);
    }

    @GetMapping ("/available")
    public ResponseEntity<List<Gift>> listAvailable() {
        List<Gift> gifts = giftService.listAvailable();
        return ResponseEntity.ok(gifts);
    }

    
}

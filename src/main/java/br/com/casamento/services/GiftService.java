package br.com.casamento.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.casamento.dtos.response.GiftGroupResponse;
import br.com.casamento.dtos.response.GiftResponse;
import br.com.casamento.entities.Gift;
import br.com.casamento.enums.GiftGroup;
import br.com.casamento.repositories.GiftRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GiftService {
    private final GiftRepository giftRepository;

    public List<GiftGroupResponse> listGrouped() {

        return giftRepository.findAll()
            .stream()
            .collect(Collectors.groupingBy(Gift::getGiftGroup))
            .entrySet()
            .stream()
            .map(entry -> {

                var presentes = entry.getValue()
                    .stream()
                    .map(gift -> new GiftResponse(
                        gift.getId(),
                        gift.getTitle(),
                        gift.getDescription(),
                        gift.getPrice(),
                        gift.getImageUrl(),
                        gift.getPurchased()
                    ))
                    .toList();

                return new GiftGroupResponse(
                    entry.getKey().name(),
                    presentes
                );
            })
            .toList();
    }

    public List<Gift> listByGroup(GiftGroup group) {
        return giftRepository.findAllByGiftGroup(group);
    }

    public List<Gift> listAvailable() {
        return giftRepository.findAllByPurchasedFalse();
    }
}

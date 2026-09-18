package br.com.casamento.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.casamento.entities.Gift;
import br.com.casamento.enums.GiftGroup;


public interface GiftRepository extends JpaRepository<Gift, UUID> {

    List<Gift> findAllByGiftGroup(GiftGroup group);

    List<Gift> findAllByPurchasedFalse();
}

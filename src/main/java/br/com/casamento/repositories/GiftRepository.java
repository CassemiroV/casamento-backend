package br.com.casamento.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.casamento.entities.Gift;
import br.com.casamento.enums.GiftGroup;
import jakarta.persistence.LockModeType;


public interface GiftRepository extends JpaRepository<Gift, UUID> {

    List<Gift> findAllByGiftGroup(GiftGroup group);

    List<Gift> findAllByPurchasedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gift g where g.id = :id")
    Optional<Gift> findByIdForUpdate(@Param("id") UUID id);
}

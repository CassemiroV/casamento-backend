package br.com.casamento.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.casamento.entities.Payment;
import br.com.casamento.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findByExternalReference(String externalReference);

    @Query("select p.gift.id from Payment p where p.gatewayOrderId = :orderId")
    Optional<UUID> findGiftIdByGatewayOrderId(@Param("orderId") String orderId);

    @Query("select p.gift.id from Payment p where p.externalReference = :reference")
    Optional<UUID> findGiftIdByExternalReference(@Param("reference") String reference);

    @Query("select p.gift.id from Payment p where p.id = :id")
    Optional<UUID> findGiftIdByPaymentId(@Param("id") UUID id);

    List<Payment> findAllByGift_IdAndStatus(UUID giftId, PaymentStatus status);

    boolean existsByGift_IdAndStatus(UUID giftId, PaymentStatus status);
}

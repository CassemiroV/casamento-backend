package br.com.casamento.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.casamento.enums.PaymentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor 
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "gift_id", nullable = false)
    private Gift gift;

    private String guestName;

    private String guestEmail;

    private BigDecimal amount;

    private String gatewayOrderId;

    private String externalReference;

    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime createdAt;

    public Payment(
        Gift gift,
        String guestName,
        String guestEmail,
        BigDecimal amount,
        String gatewayOrderId,
        String externalReference,
        String checkoutUrl,
        PaymentStatus status
    ) {
        this.gift = gift;
        this.guestName = guestName;
        this.guestEmail = guestEmail;
        this.amount = amount;
        this.gatewayOrderId = gatewayOrderId;
        this.externalReference = externalReference;
        this.checkoutUrl = checkoutUrl;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.externalReference == null) {
            this.externalReference = this.id.toString();
        }
    }
}
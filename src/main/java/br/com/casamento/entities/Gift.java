package br.com.casamento.entities;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.casamento.enums.GiftGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gifts")
@Getter
@Setter
public class Gift {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private Boolean purchased = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GiftGroup giftGroup;
}
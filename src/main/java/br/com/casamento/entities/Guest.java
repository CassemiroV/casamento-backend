package br.com.casamento.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "guests")
@Getter
@Setter
@NoArgsConstructor 
public class Guest {
    
    @Id
    @GeneratedValue
    private UUID id;

    private String guestName;

    private String guestEmail;

    private LocalDateTime createdAt;

}

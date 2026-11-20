package br.com.casamento.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.casamento.entities.Guest;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
}

package br.com.casamento.dtos.response;

import java.math.BigDecimal;
import java.util.UUID;

public record GiftResponse(
    UUID id,
    String titulo,
    String descricao,
    BigDecimal preco,
    String imagemUrl,
    Boolean comprado
) {
}
package br.com.casamento.dtos.response;

import java.util.List;

public record GiftGroupResponse(
    String grupo,
    List<GiftResponse> presentes
) {
}

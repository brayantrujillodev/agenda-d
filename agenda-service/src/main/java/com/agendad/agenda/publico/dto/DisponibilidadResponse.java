package com.agendad.agenda.publico.dto;

import java.time.LocalDate;
import java.util.List;

/** Contrato: GET /v1/publico/{slug}/disponibilidad. */
public record DisponibilidadResponse(
        LocalDate fecha,
        String zonaHoraria,
        List<CupoResponse> cupos) {
}

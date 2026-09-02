package com.agendad.agenda.publico.dto;

import java.time.Instant;
import java.util.UUID;

/** Respuesta 201 de {@code POST /v1/publico/{slug}/citas}. Contrato: {@code CitaCreada}. */
public record CitaCreadaResponse(
        UUID id,
        Instant inicio,
        Instant fin,
        String horaLocal,
        String servicioNombre,
        String profesionalNombre,
        String estado,
        String enlaceGestion) {
}

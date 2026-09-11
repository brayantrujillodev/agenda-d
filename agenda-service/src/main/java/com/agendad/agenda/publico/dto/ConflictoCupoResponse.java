package com.agendad.agenda.publico.dto;

import java.util.List;

/**
 * Respuesta 409 de {@code POST /v1/publico/{slug}/citas}: el cupo se acaba
 * de ocupar (lo rechazó la restricción {@code cita_sin_solape}). Contrato:
 * {@code ConflictoCupo}.
 */
public record ConflictoCupoResponse(
        String codigo,
        String mensaje,
        List<CupoResponse> alternativas) {
}

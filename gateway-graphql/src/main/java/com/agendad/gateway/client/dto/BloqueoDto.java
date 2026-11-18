package com.agendad.gateway.client.dto;

import java.time.Instant;

/** Contrato {@code Bloqueo} de agenda-service. */
public record BloqueoDto(String id, String profesionalId, Instant inicio, Instant fin, String motivo) {
}

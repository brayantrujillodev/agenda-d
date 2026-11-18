package com.agendad.agenda.configuracion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Cuerpo de {@code POST /v1/bloqueos}. Instantes en UTC. */
public record BloqueoRequest(
        @NotNull(message = "Falta el profesional.")
        UUID profesionalId,

        @NotNull(message = "Falta el inicio del bloqueo.")
        Instant inicio,

        @NotNull(message = "Falta el fin del bloqueo.")
        Instant fin,

        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres.")
        String motivo) {
}

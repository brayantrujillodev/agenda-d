package com.agendad.gateway.model;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Input {@code BloqueoInput}. Instantes en UTC. */
public record BloqueoInput(
        String profesionalId,
        Instant inicio,
        Instant fin,
        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres.")
        String motivo) {
}

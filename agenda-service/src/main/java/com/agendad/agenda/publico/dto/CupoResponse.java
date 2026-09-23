package com.agendad.agenda.publico.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Una franja libre. {@code inicio} y {@code fin} van en UTC; {@code horaLocal}
 * ({@code HH:mm}) es solo para mostrar al cliente.
 */
public record CupoResponse(
        Instant inicio,
        Instant fin,
        String horaLocal,
        UUID profesionalId,
        String profesionalNombre) {
}

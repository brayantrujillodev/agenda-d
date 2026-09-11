package com.agendad.agenda.outbox.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Carga útil del tópico {@code citas.reservadas}
 * (docs/eventos/CONTRATO-EVENTOS.md). Se serializa a JSON y se guarda en
 * {@code agenda.outbox.payload}; el relay la publica tal cual.
 *
 * <p>Debe coincidir campo a campo con el record espejo de
 * notificaciones-service. Cambiarlo requiere PR y aviso al grupo.
 */
public record EventoCitaReservada(
        UUID eventoId,
        int version,
        Instant ocurridoEn,
        String correlationId,

        UUID negocioId,
        UUID citaId,
        UUID profesionalId,
        String profesionalNombre,
        UUID servicioId,
        String servicioNombre,

        Instant inicio,
        Instant fin,
        String zonaHoraria,

        ClienteEvento cliente,
        String tokenGestion) {

    public static final int VERSION_ACTUAL = 1;
}

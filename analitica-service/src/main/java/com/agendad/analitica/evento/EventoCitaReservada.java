package com.agendad.analitica.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Espejo de com.agendad.agenda.outbox.evento.EventoCitaReservada
 * (docs/eventos/CONTRATO-EVENTOS.md). Debe coincidir campo a campo con el
 * productor: cambiarlo requiere PR y aviso al grupo.
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
}

package com.agendad.analitica.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Espejo de com.agendad.agenda.outbox.evento.EventoCitaCancelada
 * (docs/eventos/CONTRATO-EVENTOS.md). No trae zonaHoraria ni nombres de
 * servicio/profesional: el contrato no los incluye.
 */
public record EventoCitaCancelada(
        UUID eventoId,
        int version,
        Instant ocurridoEn,
        String correlationId,

        UUID negocioId,
        UUID citaId,
        UUID profesionalId,
        UUID servicioId,

        Instant inicio,
        Instant fin,

        String canceladaPor,
        ClienteEvento cliente) {
}

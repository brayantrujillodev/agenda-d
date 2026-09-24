package com.agendad.analitica.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Espejo de com.agendad.agenda.outbox.evento.EventoCitaEstado
 * (docs/eventos/CONTRATO-EVENTOS.md). Cierra el ciclo de la cita: alimenta
 * la tasa de inasistencia, el indicador con el que se mide si el sistema
 * sirvió.
 */
public record EventoCitaEstado(
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

        String estadoAnterior,
        String estadoNuevo) {

    public static final String ATENDIDA = "ATENDIDA";
    public static final String NO_ASISTIO = "NO_ASISTIO";
}

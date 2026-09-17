package com.agendad.agenda.outbox.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Carga útil del tópico {@code citas.estado} (docs/eventos/CONTRATO-EVENTOS.md).
 * Cierra el ciclo de la cita: alimenta la tasa de inasistencia en
 * {@code analitica-service}.
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

    public static final int VERSION_ACTUAL = 1;
}

package com.agendad.agenda.outbox.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Carga útil del tópico {@code citas.canceladas}
 * (docs/eventos/CONTRATO-EVENTOS.md). A diferencia de
 * {@link EventoCitaReservada} no lleva {@code zonaHoraria} ni los nombres
 * de servicio/profesional: el contrato no los incluye.
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

    public static final int VERSION_ACTUAL = 1;

    /** Quién originó la cancelación. */
    public static final String POR_CLIENTE = "CLIENTE";
    public static final String POR_NEGOCIO = "NEGOCIO";
}

package co.edu.fet.agendad.notificaciones.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Espejo del tópico {@code citas.canceladas}
 * (docs/eventos/CONTRATO-EVENTOS.md).
 *
 * <p>A diferencia de {@link CitaReservadaEvento}, este evento NO trae
 * {@code zonaHoraria} ni los nombres de servicio/profesional: el contrato
 * no los incluye, así que el mensaje de cancelación no puede mencionarlos.
 */
public record CitaCanceladaEvento(
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

        String canceladaPor, // CLIENTE | NEGOCIO
        ClienteInfo cliente
) {
}

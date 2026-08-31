package co.edu.fet.agendad.notificaciones.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Espejo del tópico {@code citas.reservadas}
 * (docs/eventos/CONTRATO-EVENTOS.md). No se cambia sin acordarlo con el
 * equipo: el productor (agenda-service, vía outbox) y este consumidor
 * deben coincidir campo a campo.
 */
public record CitaReservadaEvento(
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

        ClienteInfo cliente,
        String tokenGestion
) {
}

package com.agendad.agenda.publico;

import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.dominio.TokenGestion;
import com.agendad.agenda.outbox.evento.ClienteEvento;
import com.agendad.agenda.outbox.evento.EventoCitaReservada;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.OutboxRepository;
import com.agendad.agenda.repositorio.TokenGestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * El único punto donde se escribe una cita. Inserta la cita, su token de
 * gestión y la fila de outbox en la MISMA transacción (CLAUDE.md, regla 2):
 * o se guardan las tres, o ninguna. Nunca se llama a Kafka desde aquí.
 *
 * <p>No se consulta disponibilidad antes de insertar: se intenta el
 * {@code INSERT} y se deja que la restricción {@code cita_sin_solape}
 * arbitre. Si la rechaza, se traduce a una señal que el orquestador
 * ({@link ReservaService}) convierte en 409.
 */
@Component
class PersistenciaReserva {

    private static final Duration VIGENCIA_TOKEN = Duration.ofDays(120);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CitaRepository citaRepo;
    private final TokenGestionRepository tokenRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    PersistenciaReserva(CitaRepository citaRepo, TokenGestionRepository tokenRepo,
                        OutboxRepository outboxRepo, ObjectMapper objectMapper) {
        this.citaRepo = citaRepo;
        this.tokenRepo = tokenRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    Resultado crear(Datos d) {
        Cita cita = new Cita(d.negocioId(), d.servicioId(), d.profesionalId(),
                d.inicio(), d.fin(), d.clienteNombre(), d.clienteCelular(), d.idempotencyKey());
        try {
            citaRepo.saveAndFlush(cita);
        } catch (DataIntegrityViolationException e) {
            String restriccion = nombreRestriccion(e);
            if (restriccion.contains("cita_sin_solape")) {
                throw new Solape();
            }
            if (restriccion.contains("idx_cita_idempotency")) {
                throw new CarreraIdempotencia();
            }
            throw e;
        }

        String token = nuevoToken();
        tokenRepo.save(new TokenGestion(cita.getId(), token, Instant.now().plus(VIGENCIA_TOKEN)));

        EventoCitaReservada evento = new EventoCitaReservada(
                UUID.randomUUID(), EventoCitaReservada.VERSION_ACTUAL, Instant.now(), d.correlationId(),
                d.negocioId(), cita.getId(), d.profesionalId(), d.profesionalNombre(),
                d.servicioId(), d.servicioNombre(),
                cita.getInicio(), cita.getFin(), d.zonaHoraria(),
                new ClienteEvento(d.clienteNombre(), d.clienteCelular()), token);

        outboxRepo.save(new Outbox("citas.reservadas", d.profesionalId().toString(),
                d.negocioId(), serializar(evento)));

        return new Resultado(cita.getId(), cita.getInicio(), cita.getFin(), token);
    }

    private String serializar(EventoCitaReservada evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento citas.reservadas", e);
        }
    }

    /** Nombre de la restricción violada, en minúsculas; cadena vacía si no se pudo determinar. */
    private static String nombreRestriccion(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
            return cve.getConstraintName().toLowerCase(Locale.ROOT);
        }
        Throwable raiz = e.getMostSpecificCause();
        return raiz.getMessage() == null ? "" : raiz.getMessage().toLowerCase(Locale.ROOT);
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Datos ya validados y resueltos que necesita la escritura. */
    record Datos(
            UUID negocioId, UUID servicioId, UUID profesionalId,
            Instant inicio, Instant fin,
            String clienteNombre, String clienteCelular, String idempotencyKey,
            String servicioNombre, String profesionalNombre, String zonaHoraria,
            String correlationId) {
    }

    /** Lo que la escritura devuelve al orquestador para armar la respuesta. */
    record Resultado(UUID citaId, Instant inicio, Instant fin, String token) {
    }

    /** El cupo lo rechazó {@code cita_sin_solape}. */
    static final class Solape extends RuntimeException {
    }

    /** Dos peticiones con la misma {@code Idempotency-Key} llegaron a la vez. */
    static final class CarreraIdempotencia extends RuntimeException {
    }
}

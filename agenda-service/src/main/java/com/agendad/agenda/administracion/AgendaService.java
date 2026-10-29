package com.agendad.agenda.administracion;

import com.agendad.agenda.comun.dto.CitaDetalleResponse;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.outbox.evento.ClienteEvento;
import com.agendad.agenda.outbox.evento.EventoCitaCancelada;
import com.agendad.agenda.outbox.evento.EventoCitaEstado;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.OutboxRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rutas administrativas de agenda: lo que ve el negocio con su
 * {@code X-Negocio-Id}, nunca el cliente final. A diferencia de la ruta de
 * gestión por token, aquí el celular viaja completo: el negocio lo necesita
 * para llamar a su propio cliente.
 */
@Service
public class AgendaService {

    private static final Logger log = LoggerFactory.getLogger(AgendaService.class);

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final NegocioRepository negocioRepo;
    private final ProfesionalRepository profesionalRepo;
    private final ServicioRepository servicioRepo;
    private final CitaRepository citaRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public AgendaService(NegocioRepository negocioRepo, ProfesionalRepository profesionalRepo,
                         ServicioRepository servicioRepo, CitaRepository citaRepo,
                         OutboxRepository outboxRepo, ObjectMapper objectMapper) {
        this.negocioRepo = negocioRepo;
        this.profesionalRepo = profesionalRepo;
        this.servicioRepo = servicioRepo;
        this.citaRepo = citaRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    /** Citas del profesional para {@code fecha} (hora local del negocio), ordenadas por hora. */
    @Transactional(readOnly = true)
    public List<CitaDetalleResponse> agendaDelDia(UUID negocioId, UUID profesionalId, LocalDate fecha) {
        Negocio negocio = negocioRepo.findById(negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese negocio."));
        Profesional profesional = profesionalRepo.findByIdAndNegocioId(profesionalId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese profesional en este negocio."));

        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());
        Instant inicioDia = fecha.atStartOfDay(zona).toInstant();
        Instant finDia = fecha.plusDays(1).atStartOfDay(zona).toInstant();

        List<Cita> citas = citaRepo
                .findByNegocioIdAndProfesionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
                        negocioId, profesionalId, inicioDia, finDia);
        if (citas.isEmpty()) {
            return List.of();
        }

        List<UUID> servicioIds = citas.stream().map(Cita::getServicioId).distinct().toList();
        Map<UUID, String> nombrePorServicio = servicioRepo.findAllById(servicioIds).stream()
                .collect(Collectors.toMap(Servicio::getId, Servicio::getNombre));

        return citas.stream()
                .map(c -> new CitaDetalleResponse(
                        c.getId(), c.getInicio(), c.getFin(),
                        c.getInicio().atZone(zona).toLocalTime().format(HORA_LOCAL),
                        c.getServicioId(), nombrePorServicio.get(c.getServicioId()),
                        c.getProfesionalId(), profesional.getNombre(),
                        c.getClienteNombre(), c.getClienteCelular(),
                        c.getEstado().name()))
                .toList();
    }

    /** Una cita del negocio, con el celular completo (lo pide el propio negocio). */
    @Transactional(readOnly = true)
    public CitaDetalleResponse detalle(UUID negocioId, UUID citaId) {
        Cita cita = citaRepo.findByIdAndNegocioId(citaId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos esa cita en este negocio."));
        return detalleDe(cita);
    }

    /**
     * El negocio cancela una cita: libera el cupo y publica
     * {@code citas.canceladas} con {@code canceladaPor = NEGOCIO} por outbox,
     * en la misma transacción. Cancelar una ya cancelada no emite un segundo
     * evento. El motivo no está en el contrato del evento ni en el esquema:
     * queda en el log del servicio.
     */
    @Transactional
    public CitaDetalleResponse cancelar(UUID negocioId, UUID citaId, String motivo, String correlationId) {
        Cita cita = citaRepo.findByIdAndNegocioId(citaId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos esa cita en este negocio."));

        if (cita.estaCancelada()) {
            return detalleDe(cita);
        }
        if (!cita.estaConfirmada()) {
            throw new PeticionInvalida("Esa cita ya tiene un resultado registrado; no se puede cancelar.");
        }

        cita.cancelar();
        log.info("Cita {} cancelada por el negocio {}. Motivo: {}", cita.getId(), negocioId,
                motivo == null || motivo.isBlank() ? "(sin motivo)" : motivo);

        EventoCitaCancelada evento = new EventoCitaCancelada(
                UUID.randomUUID(), EventoCitaCancelada.VERSION_ACTUAL, Instant.now(),
                correlacion(correlationId),
                cita.getNegocioId(), cita.getId(), cita.getProfesionalId(), cita.getServicioId(),
                cita.getInicio(), cita.getFin(),
                EventoCitaCancelada.POR_NEGOCIO,
                new ClienteEvento(cita.getClienteNombre(), cita.getClienteCelular()));

        outboxRepo.save(new Outbox("citas.canceladas", cita.getProfesionalId().toString(),
                cita.getNegocioId(), serializar(evento)));
        return detalleDe(cita);
    }

    private CitaDetalleResponse detalleDe(Cita cita) {
        Negocio negocio = negocioRepo.findById(cita.getNegocioId())
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese negocio."));
        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());
        String servicioNombre = servicioRepo.findById(cita.getServicioId())
                .map(Servicio::getNombre).orElse(null);
        String profesionalNombre = profesionalRepo.findById(cita.getProfesionalId())
                .map(Profesional::getNombre).orElse(null);
        return new CitaDetalleResponse(
                cita.getId(), cita.getInicio(), cita.getFin(),
                cita.getInicio().atZone(zona).toLocalTime().format(HORA_LOCAL),
                cita.getServicioId(), servicioNombre, cita.getProfesionalId(), profesionalNombre,
                cita.getClienteNombre(), cita.getClienteCelular(),
                cita.getEstado().name());
    }

    /**
     * Registra el resultado de una cita ya atendida (o no). No se permite
     * sobre una cita cancelada ni sobre una que ya tenga un resultado.
     */
    @Transactional
    public void registrarEstado(UUID negocioId, UUID citaId, String estadoSolicitado, String correlationId) {
        Cita cita = citaRepo.findByIdAndNegocioId(citaId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos esa cita en este negocio."));

        if (cita.estaCancelada()) {
            throw new PeticionInvalida("Esa cita está cancelada; no se puede registrar un resultado.");
        }
        if (!cita.estaConfirmada()) {
            throw new PeticionInvalida("Esa cita ya tiene un resultado registrado.");
        }

        EstadoCita anterior = cita.getEstado();
        EstadoCita nuevo = EstadoCita.valueOf(estadoSolicitado);
        cita.marcarResultado(nuevo);

        EventoCitaEstado evento = new EventoCitaEstado(
                UUID.randomUUID(), EventoCitaEstado.VERSION_ACTUAL, Instant.now(),
                correlacion(correlationId),
                cita.getNegocioId(), cita.getId(), cita.getProfesionalId(), cita.getServicioId(),
                cita.getInicio(), cita.getFin(),
                anterior.name(), nuevo.name());

        outboxRepo.save(new Outbox("citas.estado", cita.getProfesionalId().toString(),
                cita.getNegocioId(), serializar(evento)));
    }

    private String serializar(Object evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.getClass().getSimpleName(), e);
        }
    }

    private static String correlacion(String recibida) {
        return (recibida == null || recibida.isBlank()) ? UUID.randomUUID().toString() : recibida;
    }
}

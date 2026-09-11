package com.agendad.agenda.gestion;

import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.dominio.TokenGestion;
import com.agendad.agenda.gestion.dto.CitaDetalleResponse;
import com.agendad.agenda.outbox.evento.ClienteEvento;
import com.agendad.agenda.outbox.evento.EventoCitaCancelada;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.OutboxRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.agendad.agenda.repositorio.TokenGestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de una cita por su token: consultar y cancelar. El token es
 * aleatorio y no enumerable; da acceso a UNA sola cita (RNF-09), nunca a la
 * agenda del negocio ni a datos de terceros.
 *
 * <p>Reprogramar no está en el contrato OpenAPI (solo GET y DELETE), así que
 * no se implementa aquí.
 */
@Service
public class GestionService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final TokenGestionRepository tokenRepo;
    private final CitaRepository citaRepo;
    private final NegocioRepository negocioRepo;
    private final ServicioRepository servicioRepo;
    private final ProfesionalRepository profesionalRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public GestionService(TokenGestionRepository tokenRepo, CitaRepository citaRepo,
                          NegocioRepository negocioRepo, ServicioRepository servicioRepo,
                          ProfesionalRepository profesionalRepo, OutboxRepository outboxRepo,
                          ObjectMapper objectMapper) {
        this.tokenRepo = tokenRepo;
        this.citaRepo = citaRepo;
        this.negocioRepo = negocioRepo;
        this.servicioRepo = servicioRepo;
        this.profesionalRepo = profesionalRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CitaDetalleResponse consultar(String token) {
        Cita cita = resolverCita(token);
        Negocio negocio = negocioRepo.findById(cita.getNegocioId())
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos el negocio de esta cita."));
        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());

        String servicioNombre = servicioRepo.findById(cita.getServicioId())
                .map(Servicio::getNombre).orElse(null);
        String profesionalNombre = profesionalRepo.findById(cita.getProfesionalId())
                .map(Profesional::getNombre).orElse(null);

        return new CitaDetalleResponse(
                cita.getId(), cita.getInicio(), cita.getFin(),
                cita.getInicio().atZone(zona).toLocalTime().format(HORA_LOCAL),
                servicioNombre, profesionalNombre,
                cita.getClienteNombre(), mascararCelular(cita.getClienteCelular()),
                cita.getEstado().name());
    }

    @Transactional
    public void cancelar(String token, boolean confirmar, String correlationId) {
        if (!confirmar) {
            throw new PeticionInvalida("Debes confirmar la cancelación (confirmar=true).");
        }

        Cita cita = resolverCita(token);

        if (cita.estaCancelada()) {
            return; // Ya estaba cancelada: no se emite un segundo evento.
        }
        if (!cita.estaConfirmada()) {
            throw new PeticionInvalida("Esa cita ya se cerró y no se puede cancelar.");
        }

        cita.cancelar();

        EventoCitaCancelada evento = new EventoCitaCancelada(
                UUID.randomUUID(), EventoCitaCancelada.VERSION_ACTUAL, Instant.now(),
                correlacion(correlationId),
                cita.getNegocioId(), cita.getId(), cita.getProfesionalId(), cita.getServicioId(),
                cita.getInicio(), cita.getFin(),
                EventoCitaCancelada.POR_CLIENTE,
                new ClienteEvento(cita.getClienteNombre(), cita.getClienteCelular()));

        outboxRepo.save(new Outbox("citas.canceladas", cita.getProfesionalId().toString(),
                cita.getNegocioId(), serializar(evento)));
    }

    private Cita resolverCita(String token) {
        TokenGestion tg = tokenRepo.findByToken(token)
                .orElseThrow(() -> new RecursoNoEncontrado(
                        "No encontramos esa cita. Es posible que el enlace haya vencido."));
        if (tg.getRevocadoEn() != null || tg.getExpiraEn().isBefore(Instant.now())) {
            throw new RecursoNoEncontrado("Ese enlace ya no es válido. Pídele al negocio uno nuevo.");
        }
        return citaRepo.findById(tg.getCitaId())
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos esa cita."));
    }

    private String serializar(EventoCitaCancelada evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento citas.canceladas", e);
        }
    }

    private static String correlacion(String recibida) {
        return (recibida == null || recibida.isBlank()) ? UUID.randomUUID().toString() : recibida;
    }

    /** {@code 3001234567 -> 300****567}. El contrato entrega el celular enmascarado. */
    private static String mascararCelular(String celular) {
        if (celular == null || celular.length() < 7) {
            return "***";
        }
        return celular.substring(0, 3) + "****" + celular.substring(celular.length() - 3);
    }
}

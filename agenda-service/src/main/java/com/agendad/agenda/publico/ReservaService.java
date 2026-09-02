package com.agendad.agenda.publico;

import com.agendad.agenda.comun.error.CupoOcupado;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.dominio.TokenGestion;
import com.agendad.agenda.publico.dto.CitaCreadaResponse;
import com.agendad.agenda.publico.dto.CupoResponse;
import com.agendad.agenda.publico.dto.ReservaRequest;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.agendad.agenda.repositorio.TokenGestionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reserva de citas. Orquesta: resuelve y valida negocio, servicio y
 * profesional; aplica la idempotencia; delega la escritura (cita + token +
 * outbox, misma transacción) en {@link PersistenciaReserva}; y traduce el
 * rechazo de {@code cita_sin_solape} en un 409 con los cupos más cercanos.
 *
 * <p>No consulta disponibilidad antes de insertar (CONSTRUCCION.md, Paso 2).
 */
@Service
public class ReservaService {

    private static final int MAX_ALTERNATIVAS = 5;
    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final NegocioRepository negocioRepo;
    private final ServicioRepository servicioRepo;
    private final ProfesionalRepository profesionalRepo;
    private final CitaRepository citaRepo;
    private final TokenGestionRepository tokenRepo;
    private final DisponibilidadService disponibilidadService;
    private final PersistenciaReserva persistencia;
    private final String gestionBaseUrl;

    public ReservaService(NegocioRepository negocioRepo,
                          ServicioRepository servicioRepo,
                          ProfesionalRepository profesionalRepo,
                          CitaRepository citaRepo,
                          TokenGestionRepository tokenRepo,
                          DisponibilidadService disponibilidadService,
                          PersistenciaReserva persistencia,
                          @Value("${agendad.gestion.base-url}") String gestionBaseUrl) {
        this.negocioRepo = negocioRepo;
        this.servicioRepo = servicioRepo;
        this.profesionalRepo = profesionalRepo;
        this.citaRepo = citaRepo;
        this.tokenRepo = tokenRepo;
        this.disponibilidadService = disponibilidadService;
        this.persistencia = persistencia;
        this.gestionBaseUrl = gestionBaseUrl;
    }

    public CitaCreadaResponse reservar(String slug, String idempotencyKey,
                                       String correlationId, ReservaRequest peticion) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PeticionInvalida("Falta la cabecera Idempotency-Key.");
        }

        Negocio negocio = negocioRepo.findBySlugPublico(slug)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese negocio. Revisa el enlace."));

        // Idempotencia: reenviar la misma clave devuelve la cita original.
        var reserva = citaRepo.findByNegocioIdAndIdempotencyKey(negocio.getId(), idempotencyKey);
        if (reserva.isPresent()) {
            return aRespuesta(reserva.get(), negocio);
        }

        Servicio servicio = servicioRepo.findByIdAndNegocioId(peticion.servicioId(), negocio.getId())
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese servicio en este negocio."));
        if (!servicio.isActivo()) {
            throw new RecursoNoEncontrado("Ese servicio no está disponible en este momento.");
        }

        Profesional profesional = profesionalRepo
                .findByNegocioIdAndActivoTrueAndIdInOrderByNombre(negocio.getId(), List.of(peticion.profesionalId()))
                .stream().findFirst()
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese profesional en este negocio."));

        if (!servicioRepo.findProfesionalIdsByServicioId(servicio.getId()).contains(profesional.getId())) {
            throw new PeticionInvalida("Ese profesional no atiende este servicio.");
        }

        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());
        Instant inicio = peticion.inicio();
        Instant fin = inicio.plus(Duration.ofMinutes(servicio.getDuracionMin()));

        var datos = new PersistenciaReserva.Datos(
                negocio.getId(), servicio.getId(), profesional.getId(), inicio, fin,
                peticion.clienteNombre().trim(), peticion.clienteCelular(), idempotencyKey,
                servicio.getNombre(), profesional.getNombre(), negocio.getZonaHoraria(),
                correlacion(correlationId));

        try {
            PersistenciaReserva.Resultado r = persistencia.crear(datos);
            return new CitaCreadaResponse(
                    r.citaId(), r.inicio(), r.fin(), horaLocal(r.inicio(), zona),
                    servicio.getNombre(), profesional.getNombre(),
                    EstadoCita.CONFIRMADA.name(), enlaceGestion(r.token()));
        } catch (PersistenciaReserva.Solape e) {
            throw new CupoOcupado(alternativas(negocio, servicio, profesional, inicio, zona));
        } catch (PersistenciaReserva.CarreraIdempotencia e) {
            Cita ganadora = citaRepo.findByNegocioIdAndIdempotencyKey(negocio.getId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Colisión de idempotencia sin cita registrada para " + idempotencyKey));
            return aRespuesta(ganadora, negocio);
        }
    }

    private List<CupoResponse> alternativas(Negocio negocio, Servicio servicio, Profesional profesional,
                                            Instant inicio, ZoneId zona) {
        LocalDate fecha = inicio.atZone(zona).toLocalDate();
        return disponibilidadService
                .calcular(negocio.getSlugPublico(), servicio.getId(), fecha, profesional.getId())
                .cupos().stream()
                .limit(MAX_ALTERNATIVAS)
                .toList();
    }

    private CitaCreadaResponse aRespuesta(Cita cita, Negocio negocio) {
        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());
        String servicioNombre = servicioRepo.findById(cita.getServicioId())
                .map(Servicio::getNombre).orElse(null);
        String profesionalNombre = profesionalRepo.findById(cita.getProfesionalId())
                .map(Profesional::getNombre).orElse(null);
        String enlace = tokenRepo.findFirstByCitaIdOrderByExpiraEnDesc(cita.getId())
                .map(TokenGestion::getToken).map(this::enlaceGestion).orElse(null);
        return new CitaCreadaResponse(
                cita.getId(), cita.getInicio(), cita.getFin(), horaLocal(cita.getInicio(), zona),
                servicioNombre, profesionalNombre, cita.getEstado().name(), enlace);
    }

    private static String correlacion(String recibida) {
        return (recibida == null || recibida.isBlank()) ? UUID.randomUUID().toString() : recibida;
    }

    private static String horaLocal(Instant instante, ZoneId zona) {
        return instante.atZone(zona).toLocalTime().format(HORA_LOCAL);
    }

    private String enlaceGestion(String token) {
        return gestionBaseUrl + "/v1/gestion/" + token;
    }
}

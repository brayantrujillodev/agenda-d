package com.agendad.agenda.configuracion;

import com.agendad.agenda.comun.error.Conflicto;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.configuracion.dto.BloqueoRequest;
import com.agendad.agenda.configuracion.dto.BloqueoResponse;
import com.agendad.agenda.configuracion.dto.HorarioDto;
import com.agendad.agenda.configuracion.dto.NegocioConfigResponse;
import com.agendad.agenda.configuracion.dto.ProfesionalAdminResponse;
import com.agendad.agenda.configuracion.dto.ProfesionalRequest;
import com.agendad.agenda.configuracion.dto.ServicioAdminResponse;
import com.agendad.agenda.configuracion.dto.ServicioRequest;
import com.agendad.agenda.dominio.Bloqueo;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.HorarioAtencion;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.repositorio.BloqueoRepository;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.HorarioAtencionRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuración del negocio: servicios, profesionales, horarios y bloqueos.
 * Lo usa el personal desde el panel (vía gateway-graphql). Como toda ruta
 * administrativa, cada operación filtra por el {@code negocioId} de la
 * cabecera {@code X-Negocio-Id} (CLAUDE.md, regla 4): un id de otro negocio
 * responde 404, igual que uno inexistente.
 */
@Service
public class ConfiguracionService {

    private static final Pattern HORA = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_FRANJAS = 21;
    private static final Duration MAX_BLOQUEO = Duration.ofDays(366);

    private final NegocioRepository negocioRepo;
    private final ServicioRepository servicioRepo;
    private final ProfesionalRepository profesionalRepo;
    private final HorarioAtencionRepository horarioRepo;
    private final BloqueoRepository bloqueoRepo;
    private final CitaRepository citaRepo;

    public ConfiguracionService(NegocioRepository negocioRepo, ServicioRepository servicioRepo,
                                ProfesionalRepository profesionalRepo, HorarioAtencionRepository horarioRepo,
                                BloqueoRepository bloqueoRepo, CitaRepository citaRepo) {
        this.negocioRepo = negocioRepo;
        this.servicioRepo = servicioRepo;
        this.profesionalRepo = profesionalRepo;
        this.horarioRepo = horarioRepo;
        this.bloqueoRepo = bloqueoRepo;
        this.citaRepo = citaRepo;
    }

    // -----------------------------------------------------------------
    // Negocio
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public NegocioConfigResponse negocio(UUID negocioId) {
        Negocio negocio = exigirNegocio(negocioId);

        List<ServicioAdminResponse> servicios = servicioRepo.findByNegocioIdOrderByNombre(negocioId).stream()
                .map(ServicioAdminResponse::de)
                .toList();

        Map<UUID, List<HorarioDto>> horariosPorProfesional = horarioRepo
                .findByNegocioIdOrderByDiaSemanaAscHoraInicioAsc(negocioId).stream()
                .collect(Collectors.groupingBy(HorarioAtencion::getProfesionalId,
                        Collectors.mapping(ConfiguracionService::horarioDto, Collectors.toList())));

        List<ProfesionalAdminResponse> profesionales = profesionalRepo.findByNegocioIdOrderByNombre(negocioId).stream()
                .map(p -> new ProfesionalAdminResponse(p.getId(), p.getNombre(), p.isActivo(),
                        servicioRepo.findServicioIdsByProfesionalId(p.getId()),
                        horariosPorProfesional.getOrDefault(p.getId(), List.of())))
                .toList();

        return new NegocioConfigResponse(negocio.getId(), negocio.getNombre(), negocio.getSlugPublico(),
                negocio.getZonaHoraria(), servicios, profesionales);
    }

    // -----------------------------------------------------------------
    // Servicios
    // -----------------------------------------------------------------

    @Transactional
    public ServicioAdminResponse crearServicio(UUID negocioId, ServicioRequest peticion) {
        exigirNegocio(negocioId);
        exigirMultiploDeCinco(peticion.duracionMin());
        Servicio servicio = servicioRepo.save(new Servicio(
                negocioId, peticion.nombre().strip(), peticion.duracionMin(), precio(peticion)));
        return ServicioAdminResponse.de(servicio);
    }

    @Transactional
    public ServicioAdminResponse actualizarServicio(UUID negocioId, UUID servicioId, ServicioRequest peticion) {
        Servicio servicio = exigirServicio(negocioId, servicioId);
        exigirMultiploDeCinco(peticion.duracionMin());
        servicio.actualizar(peticion.nombre().strip(), peticion.duracionMin(), precio(peticion));
        return ServicioAdminResponse.de(servicio);
    }

    /** Borrado lógico. Desactivar uno ya inactivo no es un error. */
    @Transactional
    public void desactivarServicio(UUID negocioId, UUID servicioId) {
        exigirServicio(negocioId, servicioId).desactivar();
    }

    // -----------------------------------------------------------------
    // Profesionales y horarios
    // -----------------------------------------------------------------

    @Transactional
    public ProfesionalAdminResponse crearProfesional(UUID negocioId, ProfesionalRequest peticion) {
        exigirNegocio(negocioId);

        List<UUID> serviciosIds = List.copyOf(new LinkedHashSet<>(peticion.serviciosIds()));
        for (UUID servicioId : serviciosIds) {
            Servicio servicio = servicioRepo.findByIdAndNegocioId(servicioId, negocioId)
                    .orElseThrow(() -> new PeticionInvalida(
                            "El servicio " + servicioId + " no existe en este negocio."));
            if (!servicio.isActivo()) {
                throw new PeticionInvalida("El servicio \"" + servicio.getNombre()
                        + "\" está inactivo; actívalo o elige otro.");
            }
        }

        // saveAndFlush: la fila tiene que existir antes de la tabla puente (FK).
        Profesional profesional = profesionalRepo.saveAndFlush(new Profesional(negocioId, peticion.nombre().strip()));
        serviciosIds.forEach(servicioId -> servicioRepo.asignarAProfesional(servicioId, profesional.getId()));

        return new ProfesionalAdminResponse(profesional.getId(), profesional.getNombre(), profesional.isActivo(),
                serviciosIds, List.of());
    }

    /**
     * Reemplaza TODAS las franjas del profesional. Que dos franjas del mismo
     * día no se crucen sí se valida aquí: es configuración, no reservas.
     * Lo que nunca se valida en Java es el choque entre citas (EXCLUDE).
     */
    @Transactional
    public ProfesionalAdminResponse definirHorario(UUID negocioId, UUID profesionalId, List<HorarioDto> horarios) {
        Profesional profesional = exigirProfesional(negocioId, profesionalId);
        if (horarios == null) {
            throw new PeticionInvalida("Envía la lista de franjas (puede ir vacía).");
        }
        if (horarios.size() > MAX_FRANJAS) {
            throw new PeticionInvalida("A lo sumo " + MAX_FRANJAS + " franjas por profesional (3 por día).");
        }

        List<HorarioAtencion> nuevas = new ArrayList<>();
        for (HorarioDto h : horarios) {
            if (h == null || h.diaSemana() == null || h.diaSemana() < 1 || h.diaSemana() > 7) {
                throw new PeticionInvalida("El día de la semana va de 1 (lunes) a 7 (domingo).");
            }
            LocalTime inicio = hora(h.horaInicio(), "inicio");
            LocalTime fin = hora(h.horaFin(), "fin");
            if (!fin.isAfter(inicio)) {
                throw new PeticionInvalida("En el día " + h.diaSemana()
                        + ", la hora de fin debe ser posterior a la de inicio.");
            }
            nuevas.add(new HorarioAtencion(negocioId, profesionalId, h.diaSemana().shortValue(), inicio, fin));
        }
        exigirSinCruces(nuevas);

        horarioRepo.borrarDelProfesional(profesionalId);
        horarioRepo.saveAll(nuevas);

        List<HorarioDto> guardadas = nuevas.stream()
                .sorted(Comparator.comparing(HorarioAtencion::getDiaSemana)
                        .thenComparing(HorarioAtencion::getHoraInicio))
                .map(ConfiguracionService::horarioDto)
                .toList();
        return new ProfesionalAdminResponse(profesional.getId(), profesional.getNombre(), profesional.isActivo(),
                servicioRepo.findServicioIdsByProfesionalId(profesionalId), guardadas);
    }

    // -----------------------------------------------------------------
    // Bloqueos
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BloqueoResponse> bloqueosVigentes(UUID negocioId, UUID profesionalId) {
        exigirProfesional(negocioId, profesionalId);
        return bloqueoRepo.findByNegocioIdAndProfesionalIdAndFinGreaterThanOrderByInicio(
                        negocioId, profesionalId, Instant.now()).stream()
                .map(BloqueoResponse::de)
                .toList();
    }

    /**
     * Bloquea un rango. Falla si hay citas confirmadas dentro: el sistema no
     * desagenda clientes por su cuenta.
     *
     * <p>Límite conocido: el conteo y el INSERT no son atómicos frente a una
     * reserva concurrente en el mismo rango (son tablas distintas y un
     * EXCLUDE no cruza tablas). La ventana es de milisegundos y el resultado
     * peor es una cita dentro de un bloqueo, visible en la agenda del panel;
     * nunca dos citas solapadas, que sigue impidiendo {@code cita_sin_solape}.
     */
    @Transactional
    public BloqueoResponse crearBloqueo(UUID negocioId, BloqueoRequest peticion) {
        exigirProfesional(negocioId, peticion.profesionalId());

        if (!peticion.fin().isAfter(peticion.inicio())) {
            throw new PeticionInvalida("El fin del bloqueo debe ser posterior a su inicio.");
        }
        if (Duration.between(peticion.inicio(), peticion.fin()).compareTo(MAX_BLOQUEO) > 0) {
            throw new PeticionInvalida("Un bloqueo no puede durar más de un año.");
        }
        if (!peticion.fin().isAfter(Instant.now())) {
            throw new PeticionInvalida("Ese rango ya pasó; no hace falta bloquearlo.");
        }

        long enRango = citaRepo.contarConfirmadasEnRango(
                peticion.profesionalId(), EstadoCita.CONFIRMADA, peticion.inicio(), peticion.fin());
        if (enRango > 0) {
            throw new Conflicto("CITAS_EN_RANGO", "Hay " + enRango
                    + (enRango == 1 ? " cita confirmada" : " citas confirmadas")
                    + " en ese rango. Cancélalas antes de bloquearlo.");
        }

        String motivo = peticion.motivo() == null || peticion.motivo().isBlank() ? null : peticion.motivo().strip();
        Bloqueo bloqueo = bloqueoRepo.save(new Bloqueo(
                negocioId, peticion.profesionalId(), peticion.inicio(), peticion.fin(), motivo));
        return BloqueoResponse.de(bloqueo);
    }

    // -----------------------------------------------------------------

    private Negocio exigirNegocio(UUID negocioId) {
        return negocioRepo.findById(negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese negocio."));
    }

    private Servicio exigirServicio(UUID negocioId, UUID servicioId) {
        return servicioRepo.findByIdAndNegocioId(servicioId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese servicio en este negocio."));
    }

    private Profesional exigirProfesional(UUID negocioId, UUID profesionalId) {
        return profesionalRepo.findByIdAndNegocioId(profesionalId, negocioId)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese profesional en este negocio."));
    }

    /**
     * La grilla de cupos avanza de a una duración del servicio: con 47 min,
     * los cupos quedarían en 08:00, 08:47, 09:34... Múltiplos de 5 los
     * mantienen legibles para el cliente.
     */
    private static void exigirMultiploDeCinco(int duracionMin) {
        if (duracionMin % 5 != 0) {
            throw new PeticionInvalida("La duración debe ser múltiplo de 5 minutos.");
        }
    }

    /** Misma escala que la columna NUMERIC(12,2): la respuesta no depende de cómo llegó (40000 vs 40000.00). */
    private static BigDecimal precio(ServicioRequest peticion) {
        return peticion.precio().setScale(2, RoundingMode.UNNECESSARY);
    }

    private static LocalTime hora(String texto, String cual) {
        if (texto == null || !HORA.matcher(texto).matches()) {
            throw new PeticionInvalida("La hora de " + cual + " debe tener formato HH:mm, por ejemplo 08:00.");
        }
        return LocalTime.parse(texto, HH_MM);
    }

    private static void exigirSinCruces(List<HorarioAtencion> franjas) {
        Map<Short, List<HorarioAtencion>> porDia = franjas.stream()
                .collect(Collectors.groupingBy(HorarioAtencion::getDiaSemana));
        for (List<HorarioAtencion> delDia : porDia.values()) {
            delDia.sort(Comparator.comparing(HorarioAtencion::getHoraInicio));
            for (int i = 1; i < delDia.size(); i++) {
                if (delDia.get(i).getHoraInicio().isBefore(delDia.get(i - 1).getHoraFin())) {
                    throw new PeticionInvalida("En el día " + delDia.get(i).getDiaSemana()
                            + " hay dos franjas que se cruzan.");
                }
            }
        }
    }

    private static HorarioDto horarioDto(HorarioAtencion h) {
        return new HorarioDto((int) h.getDiaSemana(), h.getHoraInicio().format(HH_MM), h.getHoraFin().format(HH_MM));
    }
}

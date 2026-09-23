package com.agendad.agenda.publico;

import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Bloqueo;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.HorarioAtencion;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.publico.dto.CupoResponse;
import com.agendad.agenda.publico.dto.DisponibilidadResponse;
import com.agendad.agenda.publico.dto.ServicioResponse;
import com.agendad.agenda.repositorio.BloqueoRepository;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.HorarioAtencionRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cálculo de disponibilidad: cruza el horario de atención con los bloqueos y
 * las citas ya tomadas. El horario está en hora local del negocio y se
 * convierte a UTC con la zona de {@code negocio.zona_horaria}.
 */
@Service
public class DisponibilidadService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final NegocioRepository negocioRepo;
    private final ServicioRepository servicioRepo;
    private final ProfesionalRepository profesionalRepo;
    private final HorarioAtencionRepository horarioRepo;
    private final BloqueoRepository bloqueoRepo;
    private final CitaRepository citaRepo;

    public DisponibilidadService(NegocioRepository negocioRepo,
                                 ServicioRepository servicioRepo,
                                 ProfesionalRepository profesionalRepo,
                                 HorarioAtencionRepository horarioRepo,
                                 BloqueoRepository bloqueoRepo,
                                 CitaRepository citaRepo) {
        this.negocioRepo = negocioRepo;
        this.servicioRepo = servicioRepo;
        this.profesionalRepo = profesionalRepo;
        this.horarioRepo = horarioRepo;
        this.bloqueoRepo = bloqueoRepo;
        this.citaRepo = citaRepo;
    }

    @Transactional(readOnly = true)
    public List<ServicioResponse> listarServicios(String slug) {
        Negocio negocio = resolverNegocio(slug);
        return servicioRepo.findByNegocioIdAndActivoTrueOrderByNombre(negocio.getId()).stream()
                .map(s -> new ServicioResponse(s.getId(), s.getNombre(), s.getDuracionMin(), s.getPrecio()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DisponibilidadResponse calcular(String slug, UUID servicioId, LocalDate fecha, UUID profesionalIdFiltro) {
        Negocio negocio = resolverNegocio(slug);

        Servicio servicio = servicioRepo.findByIdAndNegocioId(servicioId, negocio.getId())
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese servicio en este negocio."));
        if (!servicio.isActivo()) {
            throw new RecursoNoEncontrado("Ese servicio no está disponible en este momento.");
        }

        ZoneId zona = ZoneId.of(negocio.getZonaHoraria());

        // Profesionales que prestan el servicio, opcionalmente filtrados por el pedido.
        List<UUID> idsQuePrestan = servicioRepo.findProfesionalIdsByServicioId(servicio.getId());
        if (profesionalIdFiltro != null) {
            idsQuePrestan = idsQuePrestan.stream().filter(id -> id.equals(profesionalIdFiltro)).toList();
        }
        if (idsQuePrestan.isEmpty()) {
            return new DisponibilidadResponse(fecha, negocio.getZonaHoraria(), List.of());
        }

        Map<UUID, String> nombrePorProfesional = profesionalRepo
                .findByNegocioIdAndActivoTrueAndIdInOrderByNombre(negocio.getId(), idsQuePrestan).stream()
                .collect(Collectors.toMap(Profesional::getId, Profesional::getNombre));
        if (nombrePorProfesional.isEmpty()) {
            return new DisponibilidadResponse(fecha, negocio.getZonaHoraria(), List.of());
        }
        List<UUID> ids = List.copyOf(nombrePorProfesional.keySet());

        short diaSemana = (short) fecha.getDayOfWeek().getValue(); // 1 = lunes ... 7 = domingo
        List<HorarioAtencion> horarios = horarioRepo.findByProfesionalIdInAndDiaSemana(ids, diaSemana);
        if (horarios.isEmpty()) {
            return new DisponibilidadResponse(fecha, negocio.getZonaHoraria(), List.of());
        }

        // Ventana UTC que cubre el día local completo.
        Instant inicioDia = fecha.atStartOfDay(zona).toInstant();
        Instant finDia = fecha.plusDays(1).atStartOfDay(zona).toInstant();

        List<Bloqueo> bloqueos = bloqueoRepo
                .findByProfesionalIdInAndInicioLessThanAndFinGreaterThan(ids, finDia, inicioDia);
        List<Cita> ocupadas = citaRepo.buscarOcupacion(ids, EstadoCita.CANCELADA, inicioDia, finDia);

        int duracion = servicio.getDuracionMin();
        List<CupoResponse> cupos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (HorarioAtencion h : horarios) {
            UUID profesionalId = h.getProfesionalId();
            // Se trabaja en minutos desde medianoche para no arrastrar el
            // wrap de LocalDate.plusMinutes si una franja llegara cerca de las 24:00.
            int desde = h.getHoraInicio().toSecondOfDay() / 60;
            int hasta = h.getHoraFin().toSecondOfDay() / 60;

            for (int minuto = desde; minuto + duracion <= hasta; minuto += duracion) {
                LocalTime horaLocal = LocalTime.ofSecondOfDay(minuto * 60L);
                ZonedDateTime inicioZdt = ZonedDateTime.of(fecha, horaLocal, zona);
                Instant inicio = inicioZdt.toInstant();
                Instant fin = inicioZdt.plusMinutes(duracion).toInstant();

                if (!vistos.add(profesionalId + "@" + inicio)) {
                    continue;
                }
                if (libre(profesionalId, inicio, fin, bloqueos, ocupadas)) {
                    cupos.add(new CupoResponse(
                            inicio, fin, horaLocal.format(HORA_LOCAL),
                            profesionalId, nombrePorProfesional.get(profesionalId)));
                }
            }
        }

        cupos.sort(Comparator.comparing(CupoResponse::inicio).thenComparing(CupoResponse::profesionalNombre));
        return new DisponibilidadResponse(fecha, negocio.getZonaHoraria(), cupos);
    }

    private Negocio resolverNegocio(String slug) {
        return negocioRepo.findBySlugPublico(slug)
                .orElseThrow(() -> new RecursoNoEncontrado("No encontramos ese negocio. Revisa el enlace."));
    }

    /** Un cupo está libre si no se cruza con ningún bloqueo ni con ninguna cita del profesional. */
    private boolean libre(UUID profesionalId, Instant inicio, Instant fin,
                          List<Bloqueo> bloqueos, List<Cita> ocupadas) {
        boolean chocaBloqueo = bloqueos.stream().anyMatch(b ->
                b.getProfesionalId().equals(profesionalId) && seCruzan(inicio, fin, b.getInicio(), b.getFin()));
        boolean chocaCita = ocupadas.stream().anyMatch(c ->
                c.getProfesionalId().equals(profesionalId) && seCruzan(inicio, fin, c.getInicio(), c.getFin()));
        return !chocaBloqueo && !chocaCita;
    }

    /** Cruce de rangos semiabiertos [a1, a2) y [b1, b2): mismo criterio que el {@code &&} de Postgres. */
    private static boolean seCruzan(Instant a1, Instant a2, Instant b1, Instant b2) {
        return a1.isBefore(b2) && b1.isBefore(a2);
    }
}

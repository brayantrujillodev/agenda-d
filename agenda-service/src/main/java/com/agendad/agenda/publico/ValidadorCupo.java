package com.agendad.agenda.publico;

import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.dominio.HorarioAtencion;
import com.agendad.agenda.repositorio.BloqueoRepository;
import com.agendad.agenda.repositorio.HorarioAtencionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Comprueba que lo que pide el cliente sea un cupo que el negocio realmente
 * ofrece: en el futuro, dentro del horizonte de reserva, dentro del horario
 * de atención del profesional, alineado a la misma grilla que arma
 * {@link DisponibilidadService} y fuera de sus bloqueos.
 *
 * <p><b>No mira otras citas.</b> Que el cupo esté libre lo decide solo la
 * restricción {@code cita_sin_solape} en el INSERT (CLAUDE.md, regla 1).
 * Esto valida la forma de la petición contra la configuración del negocio,
 * igual que validar que el servicio exista: sin esta comprobación, por
 * Postman se podía reservar un domingo a las 03:17 o una fecha pasada.
 */
@Component
public class ValidadorCupo {

    private final HorarioAtencionRepository horarioRepo;
    private final BloqueoRepository bloqueoRepo;
    private final int diasMaximos;
    private final Clock reloj;

    @Autowired
    public ValidadorCupo(HorarioAtencionRepository horarioRepo,
                         BloqueoRepository bloqueoRepo,
                         @Value("${agendad.reserva.dias-maximos:60}") int diasMaximos) {
        this(horarioRepo, bloqueoRepo, diasMaximos, Clock.systemUTC());
    }

    ValidadorCupo(HorarioAtencionRepository horarioRepo, BloqueoRepository bloqueoRepo,
                  int diasMaximos, Clock reloj) {
        this.horarioRepo = horarioRepo;
        this.bloqueoRepo = bloqueoRepo;
        this.diasMaximos = diasMaximos;
        this.reloj = reloj;
    }

    /** Fecha consultable en {@code /disponibilidad}: de hoy (hora local del negocio) al horizonte. */
    public void validarFecha(LocalDate fecha, ZoneId zona) {
        LocalDate hoy = LocalDate.now(reloj.withZone(zona));
        if (fecha.isBefore(hoy)) {
            throw new PeticionInvalida("Esa fecha ya pasó. Elige una fecha desde hoy en adelante.");
        }
        if (fecha.isAfter(hoy.plusDays(diasMaximos))) {
            throw new PeticionInvalida(
                    "Solo se puede reservar con hasta " + diasMaximos + " días de anticipación.");
        }
    }

    public void validarCupo(UUID profesionalId, Instant inicio, int duracionMin, ZoneId zona) {
        if (!inicio.isAfter(reloj.instant())) {
            throw new PeticionInvalida("Esa hora ya pasó. Elige uno de los cupos disponibles.");
        }

        ZonedDateTime local = inicio.atZone(zona);
        validarFecha(local.toLocalDate(), zona);

        if (local.getSecond() != 0 || local.getNano() != 0 || !estaEnLaGrilla(profesionalId, local, duracionMin)) {
            throw new PeticionInvalida(
                    "Esa hora no es un cupo de este profesional. Consulta la disponibilidad y elige uno de los cupos ofrecidos.");
        }

        Instant fin = inicio.plusSeconds(duracionMin * 60L);
        boolean bloqueado = !bloqueoRepo
                .findByProfesionalIdInAndInicioLessThanAndFinGreaterThan(List.of(profesionalId), fin, inicio)
                .isEmpty();
        if (bloqueado) {
            throw new PeticionInvalida("El profesional no atiende en ese horario. Elige otro cupo.");
        }
    }

    /**
     * Mismo cálculo que {@link DisponibilidadService#calcular}: los cupos
     * arrancan en {@code hora_inicio} de la franja y avanzan de a
     * {@code duracion}; el último debe terminar a más tardar en {@code hora_fin}.
     * Si cambia la grilla allá, cambia aquí.
     */
    private boolean estaEnLaGrilla(UUID profesionalId, ZonedDateTime local, int duracion) {
        short diaSemana = (short) local.getDayOfWeek().getValue();
        int minuto = local.toLocalTime().toSecondOfDay() / 60;
        List<HorarioAtencion> franjas = horarioRepo.findByProfesionalIdInAndDiaSemana(List.of(profesionalId), diaSemana);
        return franjas.stream().anyMatch(h -> {
            int desde = h.getHoraInicio().toSecondOfDay() / 60;
            int hasta = h.getHoraFin().toSecondOfDay() / 60;
            return minuto >= desde && minuto + duracion <= hasta && (minuto - desde) % duracion == 0;
        });
    }
}

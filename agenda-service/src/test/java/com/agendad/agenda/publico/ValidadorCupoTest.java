package com.agendad.agenda.publico;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.dominio.Bloqueo;
import com.agendad.agenda.dominio.HorarioAtencion;
import com.agendad.agenda.repositorio.BloqueoRepository;
import com.agendad.agenda.repositorio.HorarioAtencionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reloj fijo en el martes 2026-09-01 07:00 de Bogotá. Laura atiende de
 * lunes a sábado de 08:00 a 18:00; el corte dura 60 minutos.
 */
class ValidadorCupoTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID LAURA = UUID.randomUUID();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    private final HorarioAtencionRepository horarioRepo = mock(HorarioAtencionRepository.class);
    private final BloqueoRepository bloqueoRepo = mock(BloqueoRepository.class);
    private final ValidadorCupo validador = new ValidadorCupo(horarioRepo, bloqueoRepo, 60, RELOJ);

    @BeforeEach
    void horarioDeLunesASabado() {
        HorarioAtencion franja = mock(HorarioAtencion.class);
        when(franja.getHoraInicio()).thenReturn(LocalTime.of(8, 0));
        when(franja.getHoraFin()).thenReturn(LocalTime.of(18, 0));
        when(horarioRepo.findByProfesionalIdInAndDiaSemana(eq(List.of(LAURA)), anyShort()))
                .thenAnswer(inv -> inv.<Short>getArgument(1) <= 6 ? List.of(franja) : List.of());
        when(bloqueoRepo.findByProfesionalIdInAndInicioLessThanAndFinGreaterThan(any(), any(), any()))
                .thenReturn(List.of());
    }

    private static Instant bogota(String fechaHora) {
        return java.time.LocalDateTime.parse(fechaHora).atZone(BOGOTA).toInstant();
    }

    @Test
    void cupoDeLaGrilla_pasa() {
        assertThatCode(() -> validador.validarCupo(LAURA, bogota("2026-09-01T10:00"), 60, BOGOTA))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.validarCupo(LAURA, bogota("2026-09-01T17:00"), 60, BOGOTA))
                .doesNotThrowAnyException();
    }

    @Test
    void horaPasada_seRechaza() {
        assertThatThrownBy(() -> validador.validarCupo(LAURA, bogota("2026-09-01T06:00"), 60, BOGOTA))
                .isInstanceOf(PeticionInvalida.class).hasMessageContaining("ya pasó");
    }

    @Test
    void masAllaDelHorizonte_seRechaza() {
        assertThatThrownBy(() -> validador.validarCupo(LAURA, bogota("2026-11-02T10:00"), 60, BOGOTA))
                .isInstanceOf(PeticionInvalida.class).hasMessageContaining("60 días");
    }

    @Test
    void fueraDelHorario_seRechaza() {
        // 03:00, 18:00 (la hora ya no cabe antes del cierre), domingo
        for (String h : List.of("2026-09-02T03:00", "2026-09-02T18:00", "2026-09-06T10:00")) {
            assertThatThrownBy(() -> validador.validarCupo(LAURA, bogota(h), 60, BOGOTA))
                    .as(h).isInstanceOf(PeticionInvalida.class).hasMessageContaining("no es un cupo");
        }
    }

    @Test
    void desalineadoDeLaGrilla_seRechaza() {
        for (String h : List.of("2026-09-02T10:30", "2026-09-02T10:17", "2026-09-02T10:00:30")) {
            assertThatThrownBy(() -> validador.validarCupo(LAURA, bogota(h), 60, BOGOTA))
                    .as(h).isInstanceOf(PeticionInvalida.class).hasMessageContaining("no es un cupo");
        }
    }

    @Test
    void dentroDeUnBloqueo_seRechaza() {
        when(bloqueoRepo.findByProfesionalIdInAndInicioLessThanAndFinGreaterThan(any(), any(), any()))
                .thenReturn(List.of(mock(Bloqueo.class)));

        assertThatThrownBy(() -> validador.validarCupo(LAURA, bogota("2026-09-02T10:00"), 60, BOGOTA))
                .isInstanceOf(PeticionInvalida.class).hasMessageContaining("no atiende");
    }

    @Test
    void fechaDeConsulta_soloDeHoyAlHorizonte() {
        assertThatCode(() -> validador.validarFecha(LocalDate.of(2026, 9, 1), BOGOTA)).doesNotThrowAnyException();
        assertThatCode(() -> validador.validarFecha(LocalDate.of(2026, 10, 31), BOGOTA)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validador.validarFecha(LocalDate.of(2026, 8, 31), BOGOTA))
                .isInstanceOf(PeticionInvalida.class);
        assertThatThrownBy(() -> validador.validarFecha(LocalDate.of(2026, 11, 1), BOGOTA))
                .isInstanceOf(PeticionInvalida.class);
    }
}

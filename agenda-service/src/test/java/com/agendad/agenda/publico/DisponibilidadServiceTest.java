package com.agendad.agenda.publico;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.repositorio.BloqueoRepository;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.HorarioAtencionRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de comportamiento del servicio con repositorios simulados. No tocan
 * base de datos: el cruce contra Postgres real lo cubre la revisión del PR.
 */
class DisponibilidadServiceTest {

    private final NegocioRepository negocioRepo = mock(NegocioRepository.class);
    private final ServicioRepository servicioRepo = mock(ServicioRepository.class);
    private final ProfesionalRepository profesionalRepo = mock(ProfesionalRepository.class);
    private final HorarioAtencionRepository horarioRepo = mock(HorarioAtencionRepository.class);
    private final BloqueoRepository bloqueoRepo = mock(BloqueoRepository.class);
    private final CitaRepository citaRepo = mock(CitaRepository.class);

    private final DisponibilidadService servicio = new DisponibilidadService(
            negocioRepo, servicioRepo, profesionalRepo, horarioRepo, bloqueoRepo, citaRepo);

    @Test
    void slugDesconocido_devuelve404() {
        when(negocioRepo.findBySlugPublico("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.calcular("no-existe", UUID.randomUUID(), LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(RecursoNoEncontrado.class)
                .hasMessageContaining("negocio");
    }

    @Test
    void servicioInexistente_devuelve404() {
        UUID negocioId = UUID.randomUUID();
        var negocio = mock(com.agendad.agenda.dominio.Negocio.class);
        when(negocio.getId()).thenReturn(negocioId);
        when(negocioRepo.findBySlugPublico("barberia-el-corte")).thenReturn(Optional.of(negocio));

        UUID servicioId = UUID.randomUUID();
        when(servicioRepo.findByIdAndNegocioId(servicioId, negocioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.calcular("barberia-el-corte", servicioId, LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(RecursoNoEncontrado.class)
                .hasMessageContaining("servicio");
    }
}

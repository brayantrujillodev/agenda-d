package com.agendad.agenda.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.error.Conflicto;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.configuracion.dto.BloqueoRequest;
import com.agendad.agenda.configuracion.dto.HorarioDto;
import com.agendad.agenda.configuracion.dto.ProfesionalRequest;
import com.agendad.agenda.configuracion.dto.ServicioRequest;
import com.agendad.agenda.dominio.Bloqueo;
import com.agendad.agenda.dominio.EstadoCita;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfiguracionServiceTest {

    private static final UUID NEGOCIO_ID = UUID.randomUUID();
    private static final UUID OTRO_NEGOCIO_ID = UUID.randomUUID();
    private static final UUID SERVICIO_ID = UUID.randomUUID();
    private static final UUID PROFESIONAL_ID = UUID.randomUUID();

    private final NegocioRepository negocioRepo = mock(NegocioRepository.class);
    private final ServicioRepository servicioRepo = mock(ServicioRepository.class);
    private final ProfesionalRepository profesionalRepo = mock(ProfesionalRepository.class);
    private final HorarioAtencionRepository horarioRepo = mock(HorarioAtencionRepository.class);
    private final BloqueoRepository bloqueoRepo = mock(BloqueoRepository.class);
    private final CitaRepository citaRepo = mock(CitaRepository.class);

    private final ConfiguracionService configuracion = new ConfiguracionService(
            negocioRepo, servicioRepo, profesionalRepo, horarioRepo, bloqueoRepo, citaRepo);

    private void negocioExiste() {
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.of(mock(Negocio.class)));
    }

    private void profesionalExiste() {
        Profesional laura = new Profesional(NEGOCIO_ID, "Laura");
        when(profesionalRepo.findByIdAndNegocioId(PROFESIONAL_ID, NEGOCIO_ID)).thenReturn(Optional.of(laura));
    }

    // --- Servicios ---------------------------------------------------

    @Test
    void crearServicio_guardaConNombreRecortadoYActivo() {
        negocioExiste();
        when(servicioRepo.save(any(Servicio.class))).thenAnswer(inv -> inv.getArgument(0));

        var creado = configuracion.crearServicio(NEGOCIO_ID,
                new ServicioRequest("  Corte y barba ", 90, new BigDecimal("35000")));

        assertThat(creado.nombre()).isEqualTo("Corte y barba");
        assertThat(creado.duracionMin()).isEqualTo(90);
        assertThat(creado.activo()).isTrue();
        assertThat(creado.precio().toPlainString()).isEqualTo("35000.00"); // misma escala que NUMERIC(12,2)
    }

    @Test
    void crearServicio_duracionQueNoEsMultiploDeCinco_esPeticionInvalida() {
        negocioExiste();

        assertThatThrownBy(() -> configuracion.crearServicio(NEGOCIO_ID,
                new ServicioRequest("Corte", 47, BigDecimal.TEN)))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("múltiplo de 5");
        verify(servicioRepo, never()).save(any());
    }

    @Test
    void crearServicio_negocioInexistente_es404() {
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configuracion.crearServicio(NEGOCIO_ID,
                new ServicioRequest("Corte", 30, BigDecimal.TEN)))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void actualizarServicio_deOtroNegocio_es404() {
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, OTRO_NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configuracion.actualizarServicio(OTRO_NEGOCIO_ID, SERVICIO_ID,
                new ServicioRequest("Corte", 30, BigDecimal.TEN)))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void desactivarServicio_esBorradoLogico() {
        Servicio corte = new Servicio(NEGOCIO_ID, "Corte", 60, BigDecimal.TEN);
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, NEGOCIO_ID)).thenReturn(Optional.of(corte));

        configuracion.desactivarServicio(NEGOCIO_ID, SERVICIO_ID);

        assertThat(corte.isActivo()).isFalse();
        verify(servicioRepo, never()).delete(any());
    }

    // --- Profesionales -----------------------------------------------

    @Test
    void crearProfesional_conServicioDeOtroNegocio_esPeticionInvalida() {
        negocioExiste();
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configuracion.crearProfesional(NEGOCIO_ID,
                new ProfesionalRequest("Camila", List.of(SERVICIO_ID))))
                .isInstanceOf(PeticionInvalida.class);
        verify(profesionalRepo, never()).saveAndFlush(any());
    }

    @Test
    void crearProfesional_conServicioInactivo_esPeticionInvalida() {
        negocioExiste();
        Servicio inactivo = new Servicio(NEGOCIO_ID, "Tinte", 60, BigDecimal.TEN);
        inactivo.desactivar();
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, NEGOCIO_ID)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> configuracion.crearProfesional(NEGOCIO_ID,
                new ProfesionalRequest("Camila", List.of(SERVICIO_ID))))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void crearProfesional_asignaCadaServicioUnaSolaVez() {
        negocioExiste();
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, NEGOCIO_ID))
                .thenReturn(Optional.of(new Servicio(NEGOCIO_ID, "Corte", 60, BigDecimal.TEN)));
        when(profesionalRepo.saveAndFlush(any(Profesional.class))).thenAnswer(inv -> inv.getArgument(0));

        var creado = configuracion.crearProfesional(NEGOCIO_ID,
                new ProfesionalRequest("Camila", List.of(SERVICIO_ID, SERVICIO_ID)));

        assertThat(creado.serviciosIds()).containsExactly(SERVICIO_ID);
        assertThat(creado.horarios()).isEmpty();
        verify(servicioRepo).asignarAProfesional(eq(SERVICIO_ID), any());
    }

    // --- Horarios ----------------------------------------------------

    @Test
    void definirHorario_reemplazaLasFranjas() {
        profesionalExiste();

        var resultado = configuracion.definirHorario(NEGOCIO_ID, PROFESIONAL_ID, List.of(
                new HorarioDto(1, "14:00", "18:00"),
                new HorarioDto(1, "08:00", "12:00")));

        verify(horarioRepo).borrarDelProfesional(PROFESIONAL_ID);
        verify(horarioRepo).saveAll(anyList());
        assertThat(resultado.horarios()).extracting(HorarioDto::horaInicio).containsExactly("08:00", "14:00");
    }

    @Test
    void definirHorario_franjasQueSeCruzan_esPeticionInvalida() {
        profesionalExiste();

        assertThatThrownBy(() -> configuracion.definirHorario(NEGOCIO_ID, PROFESIONAL_ID, List.of(
                new HorarioDto(2, "08:00", "12:00"),
                new HorarioDto(2, "11:30", "15:00"))))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("se cruzan");
        verify(horarioRepo, never()).borrarDelProfesional(any());
    }

    @Test
    void definirHorario_horaMalFormada_esPeticionInvalida() {
        profesionalExiste();

        assertThatThrownBy(() -> configuracion.definirHorario(NEGOCIO_ID, PROFESIONAL_ID, List.of(
                new HorarioDto(1, "8:00", "12:00"))))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void definirHorario_finAntesDelInicio_esPeticionInvalida() {
        profesionalExiste();

        assertThatThrownBy(() -> configuracion.definirHorario(NEGOCIO_ID, PROFESIONAL_ID, List.of(
                new HorarioDto(1, "12:00", "08:00"))))
                .isInstanceOf(PeticionInvalida.class);
    }

    @Test
    void definirHorario_diaFueraDeRango_esPeticionInvalida() {
        profesionalExiste();

        assertThatThrownBy(() -> configuracion.definirHorario(NEGOCIO_ID, PROFESIONAL_ID, List.of(
                new HorarioDto(8, "08:00", "12:00"))))
                .isInstanceOf(PeticionInvalida.class);
    }

    // --- Bloqueos ----------------------------------------------------

    @Test
    void crearBloqueo_conCitasConfirmadasEnElRango_esConflictoCitasEnRango() {
        profesionalExiste();
        Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant fin = inicio.plus(8, ChronoUnit.HOURS);
        when(citaRepo.contarConfirmadasEnRango(PROFESIONAL_ID, EstadoCita.CONFIRMADA, inicio, fin)).thenReturn(2L);

        assertThatThrownBy(() -> configuracion.crearBloqueo(NEGOCIO_ID,
                new BloqueoRequest(PROFESIONAL_ID, inicio, fin, "Vacaciones")))
                .isInstanceOf(Conflicto.class)
                .satisfies(e -> assertThat(((Conflicto) e).getCodigo()).isEqualTo("CITAS_EN_RANGO"))
                .hasMessageContaining("2 citas confirmadas");
        verify(bloqueoRepo, never()).save(any());
    }

    @Test
    void crearBloqueo_sinCitas_guarda() {
        profesionalExiste();
        Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant fin = inicio.plus(8, ChronoUnit.HOURS);
        when(citaRepo.contarConfirmadasEnRango(PROFESIONAL_ID, EstadoCita.CONFIRMADA, inicio, fin)).thenReturn(0L);
        when(bloqueoRepo.save(any(Bloqueo.class))).thenAnswer(inv -> inv.getArgument(0));

        var bloqueo = configuracion.crearBloqueo(NEGOCIO_ID,
                new BloqueoRequest(PROFESIONAL_ID, inicio, fin, "  Festivo "));

        assertThat(bloqueo.motivo()).isEqualTo("Festivo");
        assertThat(bloqueo.inicio()).isEqualTo(inicio);
    }

    @Test
    void crearBloqueo_finAntesDelInicio_esPeticionInvalida() {
        profesionalExiste();
        Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> configuracion.crearBloqueo(NEGOCIO_ID,
                new BloqueoRequest(PROFESIONAL_ID, inicio, inicio.minusSeconds(60), null)))
                .isInstanceOf(PeticionInvalida.class);
    }

    @Test
    void crearBloqueo_rangoYaPasado_esPeticionInvalida() {
        profesionalExiste();
        Instant fin = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> configuracion.crearBloqueo(NEGOCIO_ID,
                new BloqueoRequest(PROFESIONAL_ID, fin.minus(2, ChronoUnit.HOURS), fin, null)))
                .isInstanceOf(PeticionInvalida.class);
    }

    @Test
    void crearBloqueo_profesionalDeOtroNegocio_es404() {
        when(profesionalRepo.findByIdAndNegocioId(PROFESIONAL_ID, OTRO_NEGOCIO_ID)).thenReturn(Optional.empty());
        Instant inicio = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> configuracion.crearBloqueo(OTRO_NEGOCIO_ID,
                new BloqueoRequest(PROFESIONAL_ID, inicio, inicio.plusSeconds(3600), null)))
                .isInstanceOf(RecursoNoEncontrado.class);
    }
}

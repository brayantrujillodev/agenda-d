package com.agendad.agenda.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.error.CupoOcupado;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.dominio.TokenGestion;
import com.agendad.agenda.publico.dto.CitaCreadaResponse;
import com.agendad.agenda.publico.dto.CupoResponse;
import com.agendad.agenda.publico.dto.DisponibilidadResponse;
import com.agendad.agenda.publico.dto.ReservaRequest;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.agendad.agenda.repositorio.TokenGestionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comportamiento del orquestador de reserva con repositorios y persistencia
 * simulados. El cruce real contra Postgres (y el rechazo de
 * {@code cita_sin_solape}) se prueba con Testcontainers en la Fase 3.
 */
class ReservaServiceTest {

    private static final String SLUG = "barberia-el-corte";
    private static final String IDEM = "idem-1";
    private static final Instant INICIO = Instant.parse("2026-09-01T15:00:00Z"); // 10:00 en Bogotá
    private static final UUID NEGOCIO_ID = UUID.randomUUID();
    private static final UUID SERVICIO_ID = UUID.randomUUID();
    private static final UUID PROFESIONAL_ID = UUID.randomUUID();

    private final NegocioRepository negocioRepo = mock(NegocioRepository.class);
    private final ServicioRepository servicioRepo = mock(ServicioRepository.class);
    private final ProfesionalRepository profesionalRepo = mock(ProfesionalRepository.class);
    private final CitaRepository citaRepo = mock(CitaRepository.class);
    private final TokenGestionRepository tokenRepo = mock(TokenGestionRepository.class);
    private final DisponibilidadService disponibilidadService = mock(DisponibilidadService.class);
    private final PersistenciaReserva persistencia = mock(PersistenciaReserva.class);

    private final ReservaService servicio = new ReservaService(
            negocioRepo, servicioRepo, profesionalRepo, citaRepo, tokenRepo,
            disponibilidadService, persistencia, "http://localhost:8081");

    private Negocio negocio;
    private Servicio servicioBarberia;
    private Profesional laura;

    @BeforeEach
    void stubBase() {
        negocio = mock(Negocio.class);
        when(negocio.getId()).thenReturn(NEGOCIO_ID);
        when(negocio.getZonaHoraria()).thenReturn("America/Bogota");
        when(negocio.getSlugPublico()).thenReturn(SLUG);

        servicioBarberia = mock(Servicio.class);
        when(servicioBarberia.getId()).thenReturn(SERVICIO_ID);
        when(servicioBarberia.getNombre()).thenReturn("Corte de cabello");
        when(servicioBarberia.getDuracionMin()).thenReturn(60);
        when(servicioBarberia.isActivo()).thenReturn(true);

        laura = mock(Profesional.class);
        when(laura.getId()).thenReturn(PROFESIONAL_ID);
        when(laura.getNombre()).thenReturn("Laura");

        when(negocioRepo.findBySlugPublico(SLUG)).thenReturn(Optional.of(negocio));
        when(citaRepo.findByNegocioIdAndIdempotencyKey(NEGOCIO_ID, IDEM)).thenReturn(Optional.empty());
        when(servicioRepo.findByIdAndNegocioId(SERVICIO_ID, NEGOCIO_ID)).thenReturn(Optional.of(servicioBarberia));
        when(profesionalRepo.findByNegocioIdAndActivoTrueAndIdInOrderByNombre(NEGOCIO_ID, List.of(PROFESIONAL_ID)))
                .thenReturn(List.of(laura));
        when(servicioRepo.findProfesionalIdsByServicioId(SERVICIO_ID)).thenReturn(List.of(PROFESIONAL_ID));
    }

    private ReservaRequest peticion() {
        return new ReservaRequest(SERVICIO_ID, PROFESIONAL_ID, INICIO, "Juan Perez", "3001234567");
    }

    @Test
    void reservaOk_devuelveCitaConfirmadaConEnlaceDeGestion() {
        UUID citaId = UUID.randomUUID();
        Instant fin = INICIO.plusSeconds(3600);
        when(persistencia.crear(any())).thenReturn(
                new PersistenciaReserva.Resultado(citaId, INICIO, fin, "tok-abc"));

        CitaCreadaResponse resp = servicio.reservar(SLUG, IDEM, null, peticion());

        assertThat(resp.id()).isEqualTo(citaId);
        assertThat(resp.fin()).isEqualTo(fin);
        assertThat(resp.horaLocal()).isEqualTo("10:00");
        assertThat(resp.servicioNombre()).isEqualTo("Corte de cabello");
        assertThat(resp.profesionalNombre()).isEqualTo("Laura");
        assertThat(resp.estado()).isEqualTo("CONFIRMADA");
        assertThat(resp.enlaceGestion()).isEqualTo("http://localhost:8081/v1/gestion/tok-abc");
    }

    @Test
    void faltaIdempotencyKey_devuelve400() {
        assertThatThrownBy(() -> servicio.reservar(SLUG, "  ", null, peticion()))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void mismaClaveDeIdempotencia_devuelveLaCitaOriginalSinVolverAEscribir() {
        UUID citaId = UUID.randomUUID();
        Cita original = mock(Cita.class);
        when(original.getId()).thenReturn(citaId);
        when(original.getInicio()).thenReturn(INICIO);
        when(original.getFin()).thenReturn(INICIO.plusSeconds(3600));
        when(original.getServicioId()).thenReturn(SERVICIO_ID);
        when(original.getProfesionalId()).thenReturn(PROFESIONAL_ID);
        when(original.getEstado()).thenReturn(EstadoCita.CONFIRMADA);
        when(citaRepo.findByNegocioIdAndIdempotencyKey(NEGOCIO_ID, IDEM)).thenReturn(Optional.of(original));
        when(servicioRepo.findById(SERVICIO_ID)).thenReturn(Optional.of(servicioBarberia));
        when(profesionalRepo.findById(PROFESIONAL_ID)).thenReturn(Optional.of(laura));
        TokenGestion token = mock(TokenGestion.class);
        when(token.getToken()).thenReturn("tok-xyz");
        when(tokenRepo.findFirstByCitaIdOrderByExpiraEnDesc(citaId)).thenReturn(Optional.of(token));

        CitaCreadaResponse resp = servicio.reservar(SLUG, IDEM, null, peticion());

        assertThat(resp.id()).isEqualTo(citaId);
        assertThat(resp.enlaceGestion()).endsWith("/v1/gestion/tok-xyz");
        verifyNoInteractions(persistencia);
    }

    @Test
    void cupoRechazadoPorLaBase_devuelve409ConAlternativas() {
        when(persistencia.crear(any())).thenThrow(new PersistenciaReserva.Solape());
        CupoResponse alt1 = new CupoResponse(INICIO.plusSeconds(3600), INICIO.plusSeconds(7200),
                "11:00", PROFESIONAL_ID, "Laura");
        CupoResponse alt2 = new CupoResponse(INICIO.plusSeconds(7200), INICIO.plusSeconds(10800),
                "12:00", PROFESIONAL_ID, "Laura");
        when(disponibilidadService.calcular(eq(SLUG), eq(SERVICIO_ID), eq(LocalDate.of(2026, 9, 1)), eq(PROFESIONAL_ID)))
                .thenReturn(new DisponibilidadResponse(LocalDate.of(2026, 9, 1), "America/Bogota", List.of(alt1, alt2)));

        assertThatThrownBy(() -> servicio.reservar(SLUG, IDEM, null, peticion()))
                .isInstanceOf(CupoOcupado.class)
                .satisfies(e -> assertThat(((CupoOcupado) e).getAlternativas()).containsExactly(alt1, alt2));
    }

    @Test
    void profesionalQueNoPrestaElServicio_devuelve400() {
        when(servicioRepo.findProfesionalIdsByServicioId(SERVICIO_ID)).thenReturn(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> servicio.reservar(SLUG, IDEM, null, peticion()))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("no atiende este servicio");
    }
}

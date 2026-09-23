package com.agendad.agenda.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agendad.agenda.comun.dto.CitaDetalleResponse;
import com.agendad.agenda.comun.error.PeticionInvalida;
import com.agendad.agenda.comun.error.RecursoNoEncontrado;
import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import com.agendad.agenda.dominio.Negocio;
import com.agendad.agenda.dominio.Outbox;
import com.agendad.agenda.dominio.Profesional;
import com.agendad.agenda.dominio.Servicio;
import com.agendad.agenda.repositorio.CitaRepository;
import com.agendad.agenda.repositorio.NegocioRepository;
import com.agendad.agenda.repositorio.OutboxRepository;
import com.agendad.agenda.repositorio.ProfesionalRepository;
import com.agendad.agenda.repositorio.ServicioRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgendaServiceTest {

    private static final Instant INICIO = Instant.parse("2026-09-01T15:00:00Z"); // 10:00 en Bogotá
    private static final Instant FIN = INICIO.plusSeconds(3600);
    private static final LocalDate FECHA = LocalDate.parse("2026-09-01");
    private static final UUID NEGOCIO_ID = UUID.randomUUID();
    private static final UUID SERVICIO_ID = UUID.randomUUID();
    private static final UUID PROFESIONAL_ID = UUID.randomUUID();
    private static final UUID CITA_ID = UUID.randomUUID();

    private final NegocioRepository negocioRepo = mock(NegocioRepository.class);
    private final ProfesionalRepository profesionalRepo = mock(ProfesionalRepository.class);
    private final ServicioRepository servicioRepo = mock(ServicioRepository.class);
    private final CitaRepository citaRepo = mock(CitaRepository.class);
    private final OutboxRepository outboxRepo = mock(OutboxRepository.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);

    private final AgendaService servicio = new AgendaService(
            negocioRepo, profesionalRepo, servicioRepo, citaRepo, outboxRepo, objectMapper);

    private Cita citaConfirmada() {
        return new Cita(NEGOCIO_ID, SERVICIO_ID, PROFESIONAL_ID, INICIO, FIN,
                "Juan Perez", "3001234567", "idem-1");
    }

    private void stubNegocioYProfesional() {
        Negocio negocio = mock(Negocio.class);
        when(negocio.getZonaHoraria()).thenReturn("America/Bogota");
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.of(negocio));
        Profesional laura = mock(Profesional.class);
        when(laura.getNombre()).thenReturn("Laura");
        when(profesionalRepo.findByIdAndNegocioId(PROFESIONAL_ID, NEGOCIO_ID)).thenReturn(Optional.of(laura));
    }

    @Test
    void agendaDelDia_negocioInexistente_devuelve404() {
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agendaDelDia(NEGOCIO_ID, PROFESIONAL_ID, FECHA))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void agendaDelDia_profesionalNoPerteneceAlNegocio_devuelve404() {
        Negocio negocio = mock(Negocio.class);
        when(negocio.getZonaHoraria()).thenReturn("America/Bogota");
        when(negocioRepo.findById(NEGOCIO_ID)).thenReturn(Optional.of(negocio));
        when(profesionalRepo.findByIdAndNegocioId(PROFESIONAL_ID, NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.agendaDelDia(NEGOCIO_ID, PROFESIONAL_ID, FECHA))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void agendaDelDia_devuelveCitasOrdenadasConCelularCompleto() {
        stubNegocioYProfesional();
        Cita cita = citaConfirmada();
        when(citaRepo.findByNegocioIdAndProfesionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
                        eq(NEGOCIO_ID), eq(PROFESIONAL_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(cita));
        Servicio corte = mock(Servicio.class);
        when(corte.getId()).thenReturn(SERVICIO_ID);
        when(corte.getNombre()).thenReturn("Corte de cabello");
        when(servicioRepo.findAllById(any())).thenReturn(List.of(corte));

        List<CitaDetalleResponse> agenda = servicio.agendaDelDia(NEGOCIO_ID, PROFESIONAL_ID, FECHA);

        assertThat(agenda).hasSize(1);
        CitaDetalleResponse detalle = agenda.get(0);
        assertThat(detalle.clienteCelular()).isEqualTo("3001234567"); // sin enmascarar: lo pide el negocio
        assertThat(detalle.clienteNombre()).isEqualTo("Juan Perez");
        assertThat(detalle.horaLocal()).isEqualTo("10:00");
        assertThat(detalle.servicioNombre()).isEqualTo("Corte de cabello");
        assertThat(detalle.profesionalNombre()).isEqualTo("Laura");
        assertThat(detalle.estado()).isEqualTo("CONFIRMADA");
    }

    @Test
    void agendaDelDia_sinCitas_devuelveListaVacia() {
        stubNegocioYProfesional();
        when(citaRepo.findByNegocioIdAndProfesionalIdAndInicioGreaterThanEqualAndInicioLessThanOrderByInicio(
                        eq(NEGOCIO_ID), eq(PROFESIONAL_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        assertThat(servicio.agendaDelDia(NEGOCIO_ID, PROFESIONAL_ID, FECHA)).isEmpty();
    }

    @Test
    void registrarEstado_citaInexistente_devuelve404() {
        when(citaRepo.findByIdAndNegocioId(CITA_ID, NEGOCIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrarEstado(NEGOCIO_ID, CITA_ID, "ATENDIDA", null))
                .isInstanceOf(RecursoNoEncontrado.class);
    }

    @Test
    void registrarEstado_citaCancelada_devuelve400() {
        Cita cita = citaConfirmada();
        cita.cancelar();
        when(citaRepo.findByIdAndNegocioId(CITA_ID, NEGOCIO_ID)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> servicio.registrarEstado(NEGOCIO_ID, CITA_ID, "ATENDIDA", null))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("cancelada");
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void registrarEstado_citaYaCerrada_devuelve400() {
        Cita cita = citaConfirmada();
        cita.marcarResultado(EstadoCita.ATENDIDA);
        when(citaRepo.findByIdAndNegocioId(CITA_ID, NEGOCIO_ID)).thenReturn(Optional.of(cita));

        assertThatThrownBy(() -> servicio.registrarEstado(NEGOCIO_ID, CITA_ID, "NO_ASISTIO", null))
                .isInstanceOf(PeticionInvalida.class)
                .hasMessageContaining("ya tiene un resultado");
        verify(outboxRepo, never()).save(any());
    }

    @Test
    void registrarEstado_ok_marcaEstadoYEncolaEventoEnOutbox() throws JsonProcessingException {
        Cita cita = citaConfirmada();
        when(citaRepo.findByIdAndNegocioId(CITA_ID, NEGOCIO_ID)).thenReturn(Optional.of(cita));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"tipo\":\"citas.estado\"}");

        servicio.registrarEstado(NEGOCIO_ID, CITA_ID, "NO_ASISTIO", "corr-1");

        assertThat(cita.getEstado()).isEqualTo(EstadoCita.NO_ASISTIO);
        verify(outboxRepo).save(argThat((Outbox o) ->
                o.getTipoEvento().equals("citas.estado")
                        && o.getClaveParticion().equals(PROFESIONAL_ID.toString())
                        && o.getNegocioId().equals(NEGOCIO_ID)));
    }
}

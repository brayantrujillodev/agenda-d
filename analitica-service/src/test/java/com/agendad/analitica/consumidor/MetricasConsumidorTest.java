package com.agendad.analitica.consumidor;

import com.agendad.analitica.evento.ClienteEvento;
import com.agendad.analitica.evento.EventoCitaCancelada;
import com.agendad.analitica.evento.EventoCitaEstado;
import com.agendad.analitica.evento.EventoCitaReservada;
import com.agendad.analitica.repositorio.EventoProcesadoRepository;
import com.agendad.analitica.repositorio.MetricaDiariaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias con Mockito, sin Kafka ni Postgres reales: Testcontainers
 * es explícitamente Fase 3 (docs/TAREAS.md #17) y no se adelanta aquí. Esto
 * verifica la lógica de deduplicación y qué se le pide al repositorio de
 * métricas para cada tipo de evento.
 */
class MetricasConsumidorTest {

    private final EventoProcesadoRepository eventoProcesadoRepository = mock(EventoProcesadoRepository.class);
    private final MetricaDiariaRepository metricaDiariaRepository = mock(MetricaDiariaRepository.class);
    private final MetricasConsumidor consumidor =
            new MetricasConsumidor(eventoProcesadoRepository, metricaDiariaRepository, "America/Bogota");

    private static final UUID NEGOCIO_ID = UUID.randomUUID();
    private static final UUID PROFESIONAL_ID = UUID.randomUUID();
    private static final UUID SERVICIO_ID = UUID.randomUUID();

    @Test
    void unaReservaNuevaRegistraLaReservaConLosMinutosDelServicio() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        EventoCitaReservada evento = new EventoCitaReservada(
                eventoId, 1, Instant.now(), "corr-1",
                NEGOCIO_ID, UUID.randomUUID(), PROFESIONAL_ID, "Laura", SERVICIO_ID, "Corte de cabello",
                Instant.parse("2026-09-23T13:00:00Z"), Instant.parse("2026-09-23T14:00:00Z"),
                "America/Bogota",
                new ClienteEvento("Juan Pérez", "3001234567"), "tok-123");

        consumidor.alReservarCita(evento);

        // 2026-09-23T13:00:00Z en America/Bogota (UTC-5) es 08:00 del mismo día
        verify(metricaDiariaRepository).registrarReserva(
                eq(NEGOCIO_ID), eq(LocalDate.of(2026, 9, 23)), eq(PROFESIONAL_ID), eq(SERVICIO_ID), eq(60L));
        verify(eventoProcesadoRepository).save(any());
    }

    @Test
    void unEventoYaProcesadoSeIgnoraYNoTocaLaMetrica() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(true);

        EventoCitaReservada evento = new EventoCitaReservada(
                eventoId, 1, Instant.now(), "corr-2",
                NEGOCIO_ID, UUID.randomUUID(), PROFESIONAL_ID, "Laura", SERVICIO_ID, "Corte de cabello",
                Instant.now(), Instant.now().plusSeconds(3600),
                "America/Bogota", new ClienteEvento("Ana", "3009999999"), "tok-456");

        consumidor.alReservarCita(evento);

        verifyNoInteractions(metricaDiariaRepository);
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    void unaCancelacionRegistraLaCancelacion() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        EventoCitaCancelada evento = new EventoCitaCancelada(
                eventoId, 1, Instant.now(), "corr-3",
                NEGOCIO_ID, UUID.randomUUID(), PROFESIONAL_ID, SERVICIO_ID,
                Instant.parse("2026-09-23T13:00:00Z"), Instant.parse("2026-09-23T14:00:00Z"),
                "CLIENTE", new ClienteEvento("Juan Pérez", "3001234567"));

        consumidor.alCancelarCita(evento);

        verify(metricaDiariaRepository).registrarCancelacion(
                eq(NEGOCIO_ID), eq(LocalDate.of(2026, 9, 23)), eq(PROFESIONAL_ID), eq(SERVICIO_ID), eq(60L));
    }

    @Test
    void unEstadoNoAsistioSeRegistraComoTal() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        EventoCitaEstado evento = new EventoCitaEstado(
                eventoId, 1, Instant.now(), "corr-4",
                NEGOCIO_ID, UUID.randomUUID(), PROFESIONAL_ID, SERVICIO_ID,
                Instant.parse("2026-09-23T13:00:00Z"), Instant.parse("2026-09-23T14:00:00Z"),
                "CONFIRMADA", EventoCitaEstado.NO_ASISTIO);

        consumidor.alRegistrarEstado(evento);

        verify(metricaDiariaRepository).registrarAsistencia(
                eq(NEGOCIO_ID), any(LocalDate.class), eq(PROFESIONAL_ID), eq(SERVICIO_ID), eq(false));
    }

    @Test
    void unEstadoAtendidaSeRegistraComoTal() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        EventoCitaEstado evento = new EventoCitaEstado(
                eventoId, 1, Instant.now(), "corr-5",
                NEGOCIO_ID, UUID.randomUUID(), PROFESIONAL_ID, SERVICIO_ID,
                Instant.parse("2026-09-23T13:00:00Z"), Instant.parse("2026-09-23T14:00:00Z"),
                "CONFIRMADA", EventoCitaEstado.ATENDIDA);

        consumidor.alRegistrarEstado(evento);

        verify(metricaDiariaRepository).registrarAsistencia(
                eq(NEGOCIO_ID), any(LocalDate.class), eq(PROFESIONAL_ID), eq(SERVICIO_ID), eq(true));
    }
}

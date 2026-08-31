package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.canal.CanalNotificacion;
import co.edu.fet.agendad.notificaciones.dominio.EventoProcesadoRepository;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.TipoAviso;
import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
import co.edu.fet.agendad.notificaciones.evento.ClienteInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias con Mockito, sin Kafka ni Postgres reales: Testcontainers
 * es explícitamente Fase 3 (docs/TAREAS.md #17) y no se adelanta aquí. Esto
 * solo verifica la lógica de deduplicación y la construcción del aviso.
 */
class CitasEventoConsumidorTest {

    private final EventoProcesadoRepository eventoProcesadoRepository = mock(EventoProcesadoRepository.class);
    private final CanalNotificacion canalNotificacion = mock(CanalNotificacion.class);
    private final CitasEventoConsumidor consumidor =
            new CitasEventoConsumidor(eventoProcesadoRepository, canalNotificacion);

    @Test
    void unaReservaNuevaGeneraUnAvisoDeConfirmacionYQuedaMarcadaComoProcesada() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        CitaReservadaEvento evento = new CitaReservadaEvento(
                eventoId, 1, Instant.now(), "corr-1",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Laura",
                UUID.randomUUID(), "Corte de cabello",
                Instant.parse("2026-09-01T15:00:00Z"), Instant.parse("2026-09-01T16:00:00Z"),
                "America/Bogota",
                new ClienteInfo("Juan Pérez", "3001234567"),
                "tok-123");

        consumidor.alReservarCita(evento);

        ArgumentCaptor<Programacion> captor = ArgumentCaptor.forClass(Programacion.class);
        verify(canalNotificacion).enviar(captor.capture());
        Programacion aviso = captor.getValue();

        assertThat(aviso.getTipo()).isEqualTo(TipoAviso.CONFIRMACION);
        assertThat(aviso.getCitaId()).isEqualTo(evento.citaId());
        assertThat(aviso.getDestinatario()).isEqualTo("3001234567");
        assertThat(aviso.getCuerpo()).contains("Juan Pérez", "Corte de cabello", "Laura");

        verify(eventoProcesadoRepository).save(any());
    }

    @Test
    void unEventoYaProcesadoSeIgnoraYNoGeneraUnSegundoAviso() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(true);

        CitaReservadaEvento evento = new CitaReservadaEvento(
                eventoId, 1, Instant.now(), "corr-2",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Laura",
                UUID.randomUUID(), "Corte de cabello",
                Instant.now(), Instant.now().plusSeconds(3600),
                "America/Bogota",
                new ClienteInfo("Ana", "3009999999"),
                "tok-456");

        consumidor.alReservarCita(evento);

        verifyNoInteractions(canalNotificacion);
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    void unaCancelacionNuevaGeneraUnAvisoDeCancelacion() {
        UUID eventoId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        CitaCanceladaEvento evento = new CitaCanceladaEvento(
                eventoId, 1, Instant.now(), "corr-3",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-01T15:00:00Z"), Instant.parse("2026-09-01T16:00:00Z"),
                "CLIENTE",
                new ClienteInfo("Juan Pérez", "3001234567"));

        consumidor.alCancelarCita(evento);

        ArgumentCaptor<Programacion> captor = ArgumentCaptor.forClass(Programacion.class);
        verify(canalNotificacion).enviar(captor.capture());
        Programacion aviso = captor.getValue();

        assertThat(aviso.getTipo()).isEqualTo(TipoAviso.CANCELACION);
        assertThat(aviso.getCuerpo()).contains("el cliente");
    }
}

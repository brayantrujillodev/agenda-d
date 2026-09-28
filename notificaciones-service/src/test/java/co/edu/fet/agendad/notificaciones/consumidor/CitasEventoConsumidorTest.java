package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.canal.CanalNotificacion;
import co.edu.fet.agendad.notificaciones.dominio.EstadoAviso;
import co.edu.fet.agendad.notificaciones.dominio.EventoProcesadoRepository;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.ProgramacionRepository;
import co.edu.fet.agendad.notificaciones.dominio.TipoAviso;
import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
import co.edu.fet.agendad.notificaciones.evento.ClienteInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
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
 * solo verifica la lógica de deduplicación, la construcción del aviso y la
 * programación/cancelación del recordatorio de 24h (docs/TAREAS.md #18).
 */
class CitasEventoConsumidorTest {

    private final EventoProcesadoRepository eventoProcesadoRepository = mock(EventoProcesadoRepository.class);
    private final CanalNotificacion canalNotificacion = mock(CanalNotificacion.class);
    private final ProgramacionRepository programacionRepository = mock(ProgramacionRepository.class);
    private final CitasEventoConsumidor consumidor =
            new CitasEventoConsumidor(eventoProcesadoRepository, canalNotificacion, programacionRepository);

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

        // #18: también queda programado (no enviado) el recordatorio de 24h.
        ArgumentCaptor<Programacion> recordatorioCaptor = ArgumentCaptor.forClass(Programacion.class);
        verify(programacionRepository).save(recordatorioCaptor.capture());
        Programacion recordatorio = recordatorioCaptor.getValue();
        assertThat(recordatorio.getTipo()).isEqualTo(TipoAviso.RECORDATORIO_24H);
        assertThat(recordatorio.getCitaId()).isEqualTo(evento.citaId());
        assertThat(recordatorio.getEstado()).isEqualTo(EstadoAviso.PENDIENTE);
        assertThat(recordatorio.getEnviarEn()).isEqualTo(evento.inicio().minusSeconds(24 * 3600));
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

    @Test
    void cancelarUnaCitaCancelaSuRecordatorioPendiente() {
        UUID eventoId = UUID.randomUUID();
        UUID citaId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);

        Programacion recordatorioPendiente = new Programacion(
                UUID.randomUUID(), UUID.randomUUID(), citaId, TipoAviso.RECORDATORIO_24H,
                Instant.now().plusSeconds(3600), "3001234567", "Recordatorio pendiente");
        when(programacionRepository.findByCitaIdAndTipo(citaId, TipoAviso.RECORDATORIO_24H))
                .thenReturn(Optional.of(recordatorioPendiente));

        CitaCanceladaEvento evento = new CitaCanceladaEvento(
                eventoId, 1, Instant.now(), "corr-4",
                UUID.randomUUID(), citaId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-01T15:00:00Z"), Instant.parse("2026-09-01T16:00:00Z"),
                "CLIENTE", new ClienteInfo("Juan Pérez", "3001234567"));

        consumidor.alCancelarCita(evento);

        assertThat(recordatorioPendiente.getEstado()).isEqualTo(EstadoAviso.CANCELADO);
        verify(programacionRepository).save(recordatorioPendiente);
    }

    @Test
    void cancelarUnaCitaSinRecordatorioPendienteNoFalla() {
        UUID eventoId = UUID.randomUUID();
        UUID citaId = UUID.randomUUID();
        when(eventoProcesadoRepository.existsById(eventoId)).thenReturn(false);
        when(programacionRepository.findByCitaIdAndTipo(citaId, TipoAviso.RECORDATORIO_24H))
                .thenReturn(Optional.empty());

        CitaCanceladaEvento evento = new CitaCanceladaEvento(
                eventoId, 1, Instant.now(), "corr-5",
                UUID.randomUUID(), citaId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), Instant.now().plusSeconds(3600),
                "NEGOCIO", new ClienteInfo("Ana", "3009999999"));

        consumidor.alCancelarCita(evento);

        verify(programacionRepository, never()).save(any());
    }
}

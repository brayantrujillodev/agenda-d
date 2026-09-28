package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.canal.CanalNotificacion;
import co.edu.fet.agendad.notificaciones.dominio.EstadoAviso;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.ProgramacionRepository;
import co.edu.fet.agendad.notificaciones.dominio.TipoAviso;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * docs/TAREAS.md #18. No prueba el temporizador de {@code @Scheduled} en sí
 * (eso es infraestructura de Spring, no lógica propia): prueba qué hace el
 * método cuando lo llaman, que es lo único que puede tener un bug.
 */
class RecordatorioSchedulerTest {

    private final ProgramacionRepository programacionRepository = mock(ProgramacionRepository.class);
    private final CanalNotificacion canalNotificacion = mock(CanalNotificacion.class);
    private final RecordatorioScheduler scheduler =
            new RecordatorioScheduler(programacionRepository, canalNotificacion);

    @Test
    void envíaTodosLosRecordatoriosVencidos() {
        Programacion r1 = new Programacion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                TipoAviso.RECORDATORIO_24H, Instant.now().minusSeconds(60), "3001111111", "Recordatorio 1");
        Programacion r2 = new Programacion(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                TipoAviso.RECORDATORIO_24H, Instant.now().minusSeconds(30), "3002222222", "Recordatorio 2");
        when(programacionRepository.findByEstadoAndTipoAndEnviarEnLessThanEqual(
                eq(EstadoAviso.PENDIENTE), eq(TipoAviso.RECORDATORIO_24H), any(Instant.class)))
                .thenReturn(List.of(r1, r2));

        scheduler.dispararRecordatoriosVencidos();

        verify(canalNotificacion).enviar(r1);
        verify(canalNotificacion).enviar(r2);
        verify(canalNotificacion, times(2)).enviar(any());
    }

    @Test
    void sinRecordatoriosVencidosNoLlamaAlCanal() {
        when(programacionRepository.findByEstadoAndTipoAndEnviarEnLessThanEqual(
                eq(EstadoAviso.PENDIENTE), eq(TipoAviso.RECORDATORIO_24H), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.dispararRecordatoriosVencidos();

        verify(canalNotificacion, never()).enviar(any());
    }
}

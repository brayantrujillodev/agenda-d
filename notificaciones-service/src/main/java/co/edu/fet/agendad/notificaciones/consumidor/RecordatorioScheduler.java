package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.canal.CanalNotificacion;
import co.edu.fet.agendad.notificaciones.dominio.EstadoAviso;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.ProgramacionRepository;
import co.edu.fet.agendad.notificaciones.dominio.TipoAviso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * docs/TAREAS.md #18: dispara los recordatorios de 24 h vencidos.
 *
 * <p>El estado vive en {@code notificaciones.programacion}, no en memoria:
 * si el contenedor se reinicia, este método simplemente encuentra en su
 * siguiente ciclo los recordatorios cuyo {@code enviarEn} ya pasó y los
 * envía — "recuperación al reiniciar" no es un mecanismo aparte, es una
 * consecuencia de que el estado esté persistido desde el principio.
 */
@Component
public class RecordatorioScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioScheduler.class);

    private final ProgramacionRepository programacionRepository;
    private final CanalNotificacion canalNotificacion;

    public RecordatorioScheduler(ProgramacionRepository programacionRepository,
                                  CanalNotificacion canalNotificacion) {
        this.programacionRepository = programacionRepository;
        this.canalNotificacion = canalNotificacion;
    }

    @Scheduled(fixedDelayString = "${agendad.recordatorio.intervalo-ms:60000}")
    @Transactional
    public void dispararRecordatoriosVencidos() {
        List<Programacion> vencidos = programacionRepository.findByEstadoAndTipoAndEnviarEnLessThanEqual(
                EstadoAviso.PENDIENTE, TipoAviso.RECORDATORIO_24H, Instant.now());

        for (Programacion recordatorio : vencidos) {
            canalNotificacion.enviar(recordatorio);
        }
        if (!vencidos.isEmpty()) {
            log.info("Recordatorios de 24h enviados: {}", vencidos.size());
        }
    }
}

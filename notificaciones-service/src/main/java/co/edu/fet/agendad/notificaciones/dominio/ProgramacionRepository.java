package co.edu.fet.agendad.notificaciones.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramacionRepository extends JpaRepository<Programacion, UUID> {

    /** Recordatorios vencidos: pendientes cuya hora de envío ya llegó. */
    List<Programacion> findByEstadoAndTipoAndEnviarEnLessThanEqual(
            EstadoAviso estado, TipoAviso tipo, Instant limite);

    /** Para cancelar el recordatorio de una cita cuando esta se cancela. */
    Optional<Programacion> findByCitaIdAndTipo(UUID citaId, TipoAviso tipo);
}

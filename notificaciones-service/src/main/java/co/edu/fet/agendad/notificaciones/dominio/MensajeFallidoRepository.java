package co.edu.fet.agendad.notificaciones.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MensajeFallidoRepository extends JpaRepository<MensajeFallido, UUID> {

    List<MensajeFallido> findAllByOrderByFallidoEnDesc();
}

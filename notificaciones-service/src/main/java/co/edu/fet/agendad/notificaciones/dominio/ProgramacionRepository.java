package co.edu.fet.agendad.notificaciones.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProgramacionRepository extends JpaRepository<Programacion, UUID> {
}

package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Bloqueo;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueoRepository extends JpaRepository<Bloqueo, UUID> {

    /**
     * Bloqueos de esos profesionales que se cruzan con la ventana [inicioVentana, finVentana).
     * Cruce = {@code bloqueo.inicio < finVentana AND bloqueo.fin > inicioVentana}.
     */
    List<Bloqueo> findByProfesionalIdInAndInicioLessThanAndFinGreaterThan(
            Collection<UUID> profesionalIds, Instant finVentana, Instant inicioVentana);
}

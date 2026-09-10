package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Profesional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfesionalRepository extends JpaRepository<Profesional, UUID> {

    List<Profesional> findByNegocioIdAndActivoTrueAndIdInOrderByNombre(UUID negocioId, Collection<UUID> ids);
}

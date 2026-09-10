package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.HorarioAtencion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, UUID> {

    List<HorarioAtencion> findByProfesionalIdInAndDiaSemana(Collection<UUID> profesionalIds, short diaSemana);
}

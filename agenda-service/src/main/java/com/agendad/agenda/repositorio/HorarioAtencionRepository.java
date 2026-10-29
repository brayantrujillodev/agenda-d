package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.HorarioAtencion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, UUID> {

    List<HorarioAtencion> findByProfesionalIdInAndDiaSemana(Collection<UUID> profesionalIds, short diaSemana);

    List<HorarioAtencion> findByNegocioIdOrderByDiaSemanaAscHoraInicioAsc(UUID negocioId);

    List<HorarioAtencion> findByProfesionalIdOrderByDiaSemanaAscHoraInicioAsc(UUID profesionalId);

    /** El horario se reemplaza entero: se borra el anterior en la misma transacción. */
    @Modifying
    @Query("DELETE FROM HorarioAtencion h WHERE h.profesionalId = :profesionalId")
    void borrarDelProfesional(@Param("profesionalId") UUID profesionalId);
}

package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Cita;
import com.agendad.agenda.dominio.EstadoCita;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CitaRepository extends JpaRepository<Cita, UUID> {

    /**
     * Citas no canceladas de esos profesionales que se cruzan con la ventana
     * [inicioVentana, finVentana). Se usa para descartar cupos ya ocupados.
     */
    @Query("""
            SELECT c FROM Cita c
            WHERE c.profesionalId IN :profesionalIds
              AND c.estado <> :cancelada
              AND c.inicio < :finVentana
              AND c.fin > :inicioVentana
            """)
    List<Cita> buscarOcupacion(
            @Param("profesionalIds") Collection<UUID> profesionalIds,
            @Param("cancelada") EstadoCita cancelada,
            @Param("inicioVentana") Instant inicioVentana,
            @Param("finVentana") Instant finVentana);
}

package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Servicio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicioRepository extends JpaRepository<Servicio, UUID> {

    Optional<Servicio> findByIdAndNegocioId(UUID id, UUID negocioId);

    List<Servicio> findByNegocioIdAndActivoTrueOrderByNombre(UUID negocioId);

    /** Todos los servicios del negocio, activos e inactivos: la vista de configuración. */
    List<Servicio> findByNegocioIdOrderByNombre(UUID negocioId);

    /** Ids de los servicios que presta un profesional. */
    @Query(value = "SELECT servicio_id FROM agenda.servicio_profesional WHERE profesional_id = :profesionalId",
            nativeQuery = true)
    List<UUID> findServicioIdsByProfesionalId(@Param("profesionalId") UUID profesionalId);

    @Modifying
    @Query(value = "INSERT INTO agenda.servicio_profesional (servicio_id, profesional_id) VALUES (:servicioId, :profesionalId)",
            nativeQuery = true)
    void asignarAProfesional(@Param("servicioId") UUID servicioId, @Param("profesionalId") UUID profesionalId);

    /**
     * Ids de los profesionales que prestan un servicio.
     * Consulta nativa contra la tabla puente; no necesitamos mapearla como entidad.
     */
    @Query(value = "SELECT profesional_id FROM agenda.servicio_profesional WHERE servicio_id = :servicioId",
            nativeQuery = true)
    List<UUID> findProfesionalIdsByServicioId(@Param("servicioId") UUID servicioId);
}

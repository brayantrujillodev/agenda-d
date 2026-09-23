package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Servicio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicioRepository extends JpaRepository<Servicio, UUID> {

    Optional<Servicio> findByIdAndNegocioId(UUID id, UUID negocioId);

    List<Servicio> findByNegocioIdAndActivoTrueOrderByNombre(UUID negocioId);

    /**
     * Ids de los profesionales que prestan un servicio.
     * Consulta nativa contra la tabla puente; no necesitamos mapearla como entidad.
     */
    @Query(value = "SELECT profesional_id FROM agenda.servicio_profesional WHERE servicio_id = :servicioId",
            nativeQuery = true)
    List<UUID> findProfesionalIdsByServicioId(@Param("servicioId") UUID servicioId);
}

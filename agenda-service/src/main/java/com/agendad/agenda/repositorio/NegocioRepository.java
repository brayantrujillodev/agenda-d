package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Negocio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NegocioRepository extends JpaRepository<Negocio, UUID> {

    Optional<Negocio> findBySlugPublico(String slugPublico);
}

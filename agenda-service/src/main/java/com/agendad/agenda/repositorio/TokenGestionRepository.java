package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.TokenGestion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenGestionRepository extends JpaRepository<TokenGestion, UUID> {

    Optional<TokenGestion> findByToken(String token);

    Optional<TokenGestion> findFirstByCitaIdOrderByExpiraEnDesc(UUID citaId);
}

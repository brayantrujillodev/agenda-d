package com.agendad.agenda.repositorio;

import com.agendad.agenda.dominio.Outbox;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /** Lo pendiente de publicar, del más antiguo al más nuevo. El relay procesa en lotes. */
    List<Outbox> findTop100ByEnviadoEnIsNullOrderByCreadoEnAsc();
}

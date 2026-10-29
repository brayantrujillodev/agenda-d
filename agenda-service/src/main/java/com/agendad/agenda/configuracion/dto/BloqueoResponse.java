package com.agendad.agenda.configuracion.dto;

import com.agendad.agenda.dominio.Bloqueo;
import java.time.Instant;
import java.util.UUID;

/** Contrato: {@code Bloqueo}. */
public record BloqueoResponse(UUID id, UUID profesionalId, Instant inicio, Instant fin, String motivo) {

    public static BloqueoResponse de(Bloqueo b) {
        return new BloqueoResponse(b.getId(), b.getProfesionalId(), b.getInicio(), b.getFin(), b.getMotivo());
    }
}

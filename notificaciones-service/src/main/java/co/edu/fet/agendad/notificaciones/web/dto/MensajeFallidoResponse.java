package co.edu.fet.agendad.notificaciones.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MensajeFallidoResponse(
        UUID id,
        String topicoOrigen,
        String payload,
        String motivo,
        int intentos,
        Instant fallidoEn) {
}

package com.agendad.agenda.publico.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /v1/publico/{slug}/citas}. El {@code fin} no se
 * envía: lo calcula el servidor con la duración del servicio.
 */
public record ReservaRequest(
        @NotNull(message = "Falta el servicio.")
        UUID servicioId,

        @NotNull(message = "Falta el profesional.")
        UUID profesionalId,

        @NotNull(message = "Falta la hora de inicio.")
        Instant inicio,

        @NotBlank(message = "Falta el nombre.")
        @Size(min = 2, max = 120, message = "El nombre debe tener entre 2 y 120 caracteres.")
        String clienteNombre,

        @NotNull(message = "Falta el celular.")
        @Pattern(regexp = "^[0-9]{10}$", message = "El número de celular debe tener 10 dígitos.")
        String clienteCelular) {
}

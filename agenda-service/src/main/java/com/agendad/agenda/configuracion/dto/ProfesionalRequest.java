package com.agendad.agenda.configuracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Cuerpo de {@code POST /v1/profesionales}. */
public record ProfesionalRequest(
        @NotBlank(message = "Falta el nombre del profesional.")
        @Size(min = 2, max = 80, message = "El nombre del profesional debe tener entre 2 y 80 caracteres.")
        String nombre,

        @NotEmpty(message = "Indica al menos un servicio que preste el profesional.")
        @Size(max = 50, message = "Son demasiados servicios para un solo profesional.")
        List<@NotNull(message = "Hay un servicio vacío en la lista.") UUID> serviciosIds) {
}

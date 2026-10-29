package com.agendad.gateway.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Input {@code ProfesionalInput}. Ver la nota de validación en {@link ServicioInput}. */
public record ProfesionalInput(
        @NotBlank(message = "Falta el nombre del profesional.")
        @Size(min = 2, max = 80, message = "El nombre del profesional debe tener entre 2 y 80 caracteres.")
        String nombre,

        @NotEmpty(message = "Indica al menos un servicio que preste el profesional.")
        @Size(max = 50, message = "Son demasiados servicios para un solo profesional.")
        List<String> serviciosIds) {
}

package com.agendad.gateway.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** Input {@code HorarioInput}. Hora local del negocio. */
public record HorarioInput(
        @Min(value = 1, message = "El día de la semana va de 1 (lunes) a 7 (domingo).")
        @Max(value = 7, message = "El día de la semana va de 1 (lunes) a 7 (domingo).")
        int diaSemana,

        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de inicio debe tener formato HH:mm, por ejemplo 08:00.")
        String horaInicio,

        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "La hora de fin debe tener formato HH:mm, por ejemplo 18:00.")
        String horaFin) {
}

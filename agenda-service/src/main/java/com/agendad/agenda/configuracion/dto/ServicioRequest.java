package com.agendad.agenda.configuracion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Cuerpo de {@code POST /v1/servicios} y {@code PUT /v1/servicios/{id}}. */
public record ServicioRequest(
        @NotBlank(message = "Falta el nombre del servicio.")
        @Size(min = 2, max = 80, message = "El nombre del servicio debe tener entre 2 y 80 caracteres.")
        String nombre,

        @NotNull(message = "Falta la duración del servicio.")
        @Min(value = 5, message = "La duración mínima es de 5 minutos.")
        @Max(value = 480, message = "La duración máxima es de 480 minutos (8 horas).")
        Integer duracionMin,

        @NotNull(message = "Falta el precio del servicio.")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
        @DecimalMax(value = "10000000", message = "El precio no puede pasar de 10.000.000.")
        @Digits(integer = 8, fraction = 2, message = "El precio admite a lo sumo 2 decimales.")
        BigDecimal precio) {
}

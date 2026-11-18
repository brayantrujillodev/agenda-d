package com.agendad.gateway.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Input {@code ServicioInput}. El gateway valida la forma (los mismos
 * límites del contrato REST) para no gastar una llamada en algo que de
 * todas formas sería un 400; las reglas del dominio las aplica agenda-service.
 */
public record ServicioInput(
        @NotBlank(message = "Falta el nombre del servicio.")
        @Size(min = 2, max = 80, message = "El nombre del servicio debe tener entre 2 y 80 caracteres.")
        String nombre,

        @Min(value = 5, message = "La duración mínima es de 5 minutos.")
        @Max(value = 480, message = "La duración máxima es de 480 minutos (8 horas).")
        int duracionMin,

        @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
        @DecimalMax(value = "10000000", message = "El precio no puede pasar de 10.000.000.")
        BigDecimal precio) {
}

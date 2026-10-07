package com.agendad.agenda.comun.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de error del contrato: {@code { "codigo": "...", "mensaje": "..." }}.
 * {@code campo} solo viaja cuando el error es de un campo concreto del cuerpo
 * (ej. {@code clienteCelular}), para que el cliente lo marque junto al input
 * sin adivinar a partir del texto del mensaje.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespuestaError(String codigo, String mensaje, String campo) {

    public RespuestaError(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }
}

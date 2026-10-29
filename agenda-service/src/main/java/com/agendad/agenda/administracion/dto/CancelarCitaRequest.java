package com.agendad.agenda.administracion.dto;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional de {@code POST /v1/citas/{id}/cancelacion}. */
public record CancelarCitaRequest(
        @Size(max = 200, message = "El motivo no puede pasar de 200 caracteres.")
        String motivo) {
}

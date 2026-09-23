package com.agendad.agenda.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cuerpo de {@code PATCH /v1/citas/{id}/estado}. El contrato solo admite
 * {@code ATENDIDA} o {@code NO_ASISTIO}: los demás valores de
 * {@link com.agendad.agenda.dominio.EstadoCita} no son un resultado que el
 * negocio pueda registrar a mano.
 */
public record RegistrarEstadoRequest(
        @NotBlank(message = "Falta el estado.")
        @Pattern(regexp = "ATENDIDA|NO_ASISTIO", message = "El estado debe ser ATENDIDA o NO_ASISTIO.")
        String estado) {
}

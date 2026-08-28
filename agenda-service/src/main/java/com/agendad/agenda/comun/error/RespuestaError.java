package com.agendad.agenda.comun.error;

/** Cuerpo de error del contrato: {@code { "codigo": "...", "mensaje": "..." }}. */
public record RespuestaError(String codigo, String mensaje) {
}

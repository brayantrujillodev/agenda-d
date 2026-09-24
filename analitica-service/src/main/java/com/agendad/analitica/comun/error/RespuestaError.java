package com.agendad.analitica.comun.error;

/** Cuerpo de error del contrato: {@code { "codigo": "...", "mensaje": "..." }}. */
public record RespuestaError(String codigo, String mensaje) {
}

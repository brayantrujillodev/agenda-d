package com.agendad.analitica.web.dto;

/**
 * Reservado para cuando se resuelva el nombre real del servicio: analitica.*
 * solo guarda servicioId (sin FK a agenda.servicio, esquemas independientes
 * por diseño — ver CLAUDE.md). Hasta entonces, GET /v1/metricas devuelve
 * topServicios vacío en vez de mostrar un UUID donde se espera un nombre.
 */
public record ConteoServicioResponse(String servicio, int cantidad) {
}

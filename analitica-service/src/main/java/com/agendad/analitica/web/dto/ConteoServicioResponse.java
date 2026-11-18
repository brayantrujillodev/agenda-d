package com.agendad.analitica.web.dto;

import java.util.UUID;

/**
 * Reservas de un servicio en el periodo. Va el {@code servicioId}, no el
 * nombre: analitica.* no conoce los nombres (viven en agenda.servicio, otro
 * esquema y sin FK por diseño — ver CLAUDE.md). Ponerle nombre lo hace
 * gateway-graphql, que es quien compone datos de los dos servicios.
 */
public record ConteoServicioResponse(UUID servicioId, long cantidad) {
}

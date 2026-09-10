package com.agendad.agenda.publico.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Un servicio ofrecido por el negocio. Contrato: GET /v1/publico/{slug}/servicios. */
public record ServicioResponse(UUID id, String nombre, int duracionMin, BigDecimal precio) {
}

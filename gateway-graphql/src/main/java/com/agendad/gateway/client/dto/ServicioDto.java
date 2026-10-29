package com.agendad.gateway.client.dto;

import java.math.BigDecimal;

/** Contrato {@code ServicioAdmin} de agenda-service. */
public record ServicioDto(String id, String nombre, int duracionMin, BigDecimal precio, boolean activo) {
}

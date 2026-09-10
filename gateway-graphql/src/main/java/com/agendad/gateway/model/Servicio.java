package com.agendad.gateway.model;

import java.math.BigDecimal;

public record Servicio(String id, String nombre, int duracionMin, BigDecimal precio, boolean activo) {
}
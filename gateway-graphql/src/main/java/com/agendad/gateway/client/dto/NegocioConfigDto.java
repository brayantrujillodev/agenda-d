package com.agendad.gateway.client.dto;

import java.util.List;

/** {@code GET /v1/negocio} de agenda-service. */
public record NegocioConfigDto(String id, String nombre, String slugPublico, String zonaHoraria,
                               List<ServicioDto> servicios, List<ProfesionalDto> profesionales) {
}

package com.agendad.gateway.client.dto;

import java.util.List;

/** Contrato {@code ProfesionalAdmin} de agenda-service. */
public record ProfesionalDto(String id, String nombre, boolean activo,
                             List<String> serviciosIds, List<HorarioDto> horarios) {
}

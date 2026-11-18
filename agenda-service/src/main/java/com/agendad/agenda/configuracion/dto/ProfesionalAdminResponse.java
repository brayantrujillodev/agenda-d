package com.agendad.agenda.configuracion.dto;

import java.util.List;
import java.util.UUID;

/** Profesional con sus servicios y su horario. Contrato: {@code ProfesionalAdmin}. */
public record ProfesionalAdminResponse(
        UUID id, String nombre, boolean activo, List<UUID> serviciosIds, List<HorarioDto> horarios) {
}

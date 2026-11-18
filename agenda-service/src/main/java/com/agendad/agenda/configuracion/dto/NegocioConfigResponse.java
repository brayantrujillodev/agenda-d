package com.agendad.agenda.configuracion.dto;

import java.util.List;
import java.util.UUID;

/** Configuración completa del negocio. Contrato: {@code NegocioConfig}. */
public record NegocioConfigResponse(
        UUID id,
        String nombre,
        String slugPublico,
        String zonaHoraria,
        List<ServicioAdminResponse> servicios,
        List<ProfesionalAdminResponse> profesionales) {
}

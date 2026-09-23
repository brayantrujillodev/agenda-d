package com.agendad.agenda.comun.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Detalle de una cita. Contrato: {@code CitaDetalle}. Lo usan dos rutas con
 * criterios distintos de enmascarado: gestión por token (celular enmascarado,
 * da acceso a una sola cita) y agenda del profesional (celular completo, la
 * pide el propio negocio).
 */
public record CitaDetalleResponse(
        UUID id,
        Instant inicio,
        Instant fin,
        String horaLocal,
        String servicioNombre,
        String profesionalNombre,
        String clienteNombre,
        String clienteCelular,
        String estado) {
}

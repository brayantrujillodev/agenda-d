package com.agendad.agenda.gestion.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Detalle de una cita para la ruta de gestión por token. Contrato:
 * {@code CitaDetalle}. El celular va enmascarado ({@code 300****567}): esta
 * ruta da acceso a UNA cita, no a datos de terceros.
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

package com.agendad.gateway.client.dto;

import java.time.Instant;

/** Contrato {@code CitaDetalle} de agenda-service. El celular llega completo. */
public record CitaDto(String id, Instant inicio, Instant fin, String horaLocal,
                      String servicioId, String servicioNombre,
                      String profesionalId, String profesionalNombre,
                      String clienteNombre, String clienteCelular, String estado) {
}

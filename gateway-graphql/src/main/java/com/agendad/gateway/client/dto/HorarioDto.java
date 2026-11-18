package com.agendad.gateway.client.dto;

/** Contrato {@code Horario} de agenda-service. Hora local del negocio, {@code HH:mm}. */
public record HorarioDto(int diaSemana, String horaInicio, String horaFin) {
}

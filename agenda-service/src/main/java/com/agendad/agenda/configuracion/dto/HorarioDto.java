package com.agendad.agenda.configuracion.dto;

/**
 * Franja semanal en hora local del negocio. Contrato: {@code Horario}.
 * Las horas viajan como texto {@code HH:mm}; las valida
 * {@code ConfiguracionService#definirHorario} para dar un mensaje claro.
 */
public record HorarioDto(Integer diaSemana, String horaInicio, String horaFin) {
}

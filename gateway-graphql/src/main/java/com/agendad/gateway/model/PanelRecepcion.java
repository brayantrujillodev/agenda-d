package com.agendad.gateway.model;

import java.time.LocalDate;

/**
 * Tipo {@code PanelRecepcion}. Solo guarda la fecha: {@code negocio},
 * {@code citasDelDia} y {@code metricasDelMes} se resuelven cada uno por su
 * lado ({@code PanelController}), así un fallo de analitica-service deja
 * {@code metricasDelMes} en null sin tumbar la agenda del día.
 */
public record PanelRecepcion(LocalDate fecha) {
}

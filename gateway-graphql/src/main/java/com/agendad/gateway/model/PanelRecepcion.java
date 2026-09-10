package com.agendad.gateway.model;

import java.time.LocalDate;
import java.util.List;

public record PanelRecepcion(LocalDate fecha, Negocio negocio, List<Cita> citasDelDia,
                             Metricas metricasDelMes) {
}
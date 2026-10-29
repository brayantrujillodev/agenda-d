package com.agendad.gateway.model;

import com.agendad.gateway.client.dto.MetricasDto.ConteoServicioDto;
import java.time.LocalDate;
import java.util.List;

/**
 * Tipo {@code Metricas}. {@code topServicios} lo resuelve
 * {@code PanelController#topServicios}: analitica-service solo conoce el
 * {@code servicioId} y el nombre sale del catálogo de agenda-service, que
 * solo se consulta si la consulta pide ese campo.
 */
public record Metricas(LocalDate desde, LocalDate hasta, int totalCitas, int atendidas,
                       int noAsistio, int canceladas, double ocupacion,
                       double tasaInasistencia, List<ConteoServicioDto> conteosPorServicio) {
}

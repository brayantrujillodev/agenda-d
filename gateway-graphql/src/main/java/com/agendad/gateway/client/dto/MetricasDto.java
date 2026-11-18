package com.agendad.gateway.client.dto;

import java.time.LocalDate;
import java.util.List;

/** {@code GET /v1/metricas} de analitica-service. */
public record MetricasDto(LocalDate desde, LocalDate hasta, long totalCitas, long atendidas,
                          long noAsistio, long canceladas, double ocupacion, double tasaInasistencia,
                          List<ConteoServicioDto> topServicios) {

    /** Trae el {@code servicioId}, no el nombre: lo pone el gateway con el catálogo de agenda. */
    public record ConteoServicioDto(String servicioId, long cantidad) {
    }
}

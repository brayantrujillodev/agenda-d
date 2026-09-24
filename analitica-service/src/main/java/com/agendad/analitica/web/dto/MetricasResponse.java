package com.agendad.analitica.web.dto;

import java.time.LocalDate;
import java.util.List;

public record MetricasResponse(
        LocalDate desde,
        LocalDate hasta,
        long totalCitas,
        long atendidas,
        long noAsistio,
        long canceladas,
        double ocupacion,
        double tasaInasistencia,
        List<ConteoServicioResponse> topServicios) {
}

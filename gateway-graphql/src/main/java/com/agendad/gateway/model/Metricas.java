package com.agendad.gateway.model;

import java.time.LocalDate;
import java.util.List;

public record Metricas(LocalDate desde, LocalDate hasta, int totalCitas, int atendidas,
                       int noAsistio, int canceladas, double ocupacion,
                       double tasaInasistencia, List<ConteoServicio> topServicios) {
}
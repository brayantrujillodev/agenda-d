package com.agendad.analitica.web;

import com.agendad.analitica.comun.error.PeticionInvalida;
import com.agendad.analitica.repositorio.AgregadoMetricas;
import com.agendad.analitica.repositorio.MetricaDiariaRepository;
import com.agendad.analitica.web.dto.MetricasResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class MetricasController {

    private final MetricaDiariaRepository metricaDiariaRepository;
    private final long minutosHabilesDia;

    public MetricasController(MetricaDiariaRepository metricaDiariaRepository,
                               @Value("${agendad.metricas.minutos-habiles-dia}") long minutosHabilesDia) {
        this.metricaDiariaRepository = metricaDiariaRepository;
        this.minutosHabilesDia = minutosHabilesDia;
    }

    @GetMapping("/v1/metricas")
    public MetricasResponse metricas(
            @RequestHeader("X-Negocio-Id") UUID negocioId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        if (desde.isAfter(hasta)) {
            throw new PeticionInvalida("La fecha \"desde\" no puede ser posterior a \"hasta\".");
        }

        AgregadoMetricas agregado = metricaDiariaRepository.agregarPeriodo(negocioId, desde, hasta);

        long minutosDisponibles = agregado.diasProfesionalConActividad() * minutosHabilesDia;
        double ocupacion = minutosDisponibles == 0
                ? 0.0
                : porcentaje(agregado.minutosOcupados(), minutosDisponibles);

        long citasResueltas = agregado.atendidas() + agregado.noAsistio();
        double tasaInasistencia = citasResueltas == 0
                ? 0.0
                : porcentaje(agregado.noAsistio(), citasResueltas);

        return new MetricasResponse(
                desde, hasta,
                agregado.reservadas(), agregado.atendidas(), agregado.noAsistio(), agregado.canceladas(),
                redondear(ocupacion), redondear(tasaInasistencia),
                List.of()); // topServicios: ver nota en ConteoServicioResponse
    }

    private double porcentaje(long parte, long total) {
        return (parte * 100.0) / total;
    }

    private double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}

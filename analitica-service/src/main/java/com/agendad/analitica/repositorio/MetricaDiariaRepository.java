package com.agendad.analitica.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * analitica.metrica_diaria es una proyección de lectura (contador), no una
 * entidad de dominio con identidad propia: se actualiza con upserts
 * atómicos, no con el ciclo cargar-modificar-guardar de un ORM. Por eso usa
 * JdbcTemplate directo en vez de Spring Data JPA — evita perder incrementos
 * concurrentes cuando Kafka entrega varios eventos casi al tiempo (el
 * tópico tiene 3 particiones).
 */
@Repository
public class MetricaDiariaRepository {

    private final JdbcTemplate jdbc;

    public MetricaDiariaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrarReserva(UUID negocioId, LocalDate fecha, UUID profesionalId, UUID servicioId, long minutos) {
        jdbc.update("""
                INSERT INTO analitica.metrica_diaria
                    (negocio_id, fecha, profesional_id, servicio_id, reservadas, minutos_ocupados)
                VALUES (?, ?, ?, ?, 1, ?)
                ON CONFLICT (negocio_id, fecha, profesional_id, servicio_id)
                DO UPDATE SET reservadas = analitica.metrica_diaria.reservadas + 1,
                              minutos_ocupados = analitica.metrica_diaria.minutos_ocupados + EXCLUDED.minutos_ocupados
                """, negocioId, fecha, profesionalId, servicioId, minutos);
    }

    public void registrarCancelacion(UUID negocioId, LocalDate fecha, UUID profesionalId, UUID servicioId, long minutos) {
        jdbc.update("""
                INSERT INTO analitica.metrica_diaria
                    (negocio_id, fecha, profesional_id, servicio_id, canceladas)
                VALUES (?, ?, ?, ?, 1)
                ON CONFLICT (negocio_id, fecha, profesional_id, servicio_id)
                DO UPDATE SET canceladas = analitica.metrica_diaria.canceladas + 1,
                              minutos_ocupados = GREATEST(analitica.metrica_diaria.minutos_ocupados - ?, 0)
                """, negocioId, fecha, profesionalId, servicioId, minutos);
    }

    public void registrarAsistencia(UUID negocioId, LocalDate fecha, UUID profesionalId, UUID servicioId, boolean atendida) {
        int incrementoAtendidas = atendida ? 1 : 0;
        int incrementoNoAsistio = atendida ? 0 : 1;
        jdbc.update("""
                INSERT INTO analitica.metrica_diaria
                    (negocio_id, fecha, profesional_id, servicio_id, atendidas, no_asistio)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (negocio_id, fecha, profesional_id, servicio_id)
                DO UPDATE SET atendidas = analitica.metrica_diaria.atendidas + EXCLUDED.atendidas,
                              no_asistio = analitica.metrica_diaria.no_asistio + EXCLUDED.no_asistio
                """, negocioId, fecha, profesionalId, servicioId, incrementoAtendidas, incrementoNoAsistio);
    }

    public AgregadoMetricas agregarPeriodo(UUID negocioId, LocalDate desde, LocalDate hasta) {
        return jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM(reservadas), 0)        AS reservadas,
                    COALESCE(SUM(canceladas), 0)        AS canceladas,
                    COALESCE(SUM(atendidas), 0)         AS atendidas,
                    COALESCE(SUM(no_asistio), 0)        AS no_asistio,
                    COALESCE(SUM(minutos_ocupados), 0)  AS minutos_ocupados,
                    COUNT(DISTINCT (profesional_id, fecha)) AS dias_profesional
                FROM analitica.metrica_diaria
                WHERE negocio_id = ? AND fecha BETWEEN ? AND ?
                """,
                (rs, rowNum) -> new AgregadoMetricas(
                        rs.getLong("reservadas"),
                        rs.getLong("canceladas"),
                        rs.getLong("atendidas"),
                        rs.getLong("no_asistio"),
                        rs.getLong("minutos_ocupados"),
                        rs.getLong("dias_profesional")),
                negocioId, desde, hasta);
    }
}

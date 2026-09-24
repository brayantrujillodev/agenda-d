package com.agendad.analitica.repositorio;

/** Resultado crudo de sumar analitica.metrica_diaria en un rango de fechas. */
public record AgregadoMetricas(
        long reservadas,
        long canceladas,
        long atendidas,
        long noAsistio,
        long minutosOcupados,
        long diasProfesionalConActividad) {

    public static AgregadoMetricas vacio() {
        return new AgregadoMetricas(0, 0, 0, 0, 0, 0);
    }
}

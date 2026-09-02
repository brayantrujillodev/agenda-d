package com.agendad.agenda.comun.error;

import com.agendad.agenda.publico.dto.CupoResponse;
import java.util.List;

/**
 * El cupo pedido se acaba de ocupar: lo rechazó la restricción
 * {@code cita_sin_solape} de PostgreSQL entre el intento de {@code INSERT}
 * y el commit. Se traduce a un 409 con los cupos más cercanos.
 */
public class CupoOcupado extends RuntimeException {

    private final transient List<CupoResponse> alternativas;

    public CupoOcupado(List<CupoResponse> alternativas) {
        super("Ese cupo se acaba de ocupar. Estos son los más cercanos.");
        this.alternativas = alternativas;
    }

    public List<CupoResponse> getAlternativas() {
        return alternativas;
    }
}

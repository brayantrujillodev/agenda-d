package com.agendad.agenda.comun.error;

/**
 * La operación choca con el estado actual de los datos (409). Lleva su
 * propio {@code codigo} del contrato, p. ej. {@code CITAS_EN_RANGO}.
 */
public class Conflicto extends RuntimeException {

    private final String codigo;

    public Conflicto(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

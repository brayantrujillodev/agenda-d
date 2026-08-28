package com.agendad.agenda.comun.error;

/** Datos de la solicitud inválidos o incoherentes. Se traduce a 400. */
public class PeticionInvalida extends RuntimeException {

    public PeticionInvalida(String mensaje) {
        super(mensaje);
    }
}

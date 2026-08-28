package com.agendad.agenda.comun.error;

/** El negocio, servicio o cita solicitado no existe. Se traduce a 404. */
public class RecursoNoEncontrado extends RuntimeException {

    public RecursoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}

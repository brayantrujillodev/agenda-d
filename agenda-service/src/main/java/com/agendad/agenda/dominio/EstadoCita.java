package com.agendad.agenda.dominio;

/** Estados válidos de una cita. Coinciden con el CHECK de la tabla {@code agenda.cita}. */
public enum EstadoCita {
    CONFIRMADA,
    CANCELADA,
    ATENDIDA,
    NO_ASISTIO
}

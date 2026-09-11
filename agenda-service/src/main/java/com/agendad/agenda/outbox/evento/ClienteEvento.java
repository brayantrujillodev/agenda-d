package com.agendad.agenda.outbox.evento;

/** Sub-objeto {@code cliente} de los eventos de cita (docs/eventos/CONTRATO-EVENTOS.md). */
public record ClienteEvento(String nombre, String celular) {
}

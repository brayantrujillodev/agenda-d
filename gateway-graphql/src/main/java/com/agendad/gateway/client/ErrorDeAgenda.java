package com.agendad.gateway.client;

/**
 * agenda-service rechazó o no recibió una escritura del panel. El mensaje
 * ya viene en español y accionable (CLAUDE.md): el resolver de GraphQL lo
 * entrega tal cual en {@code errors[].message}.
 */
public class ErrorDeAgenda extends RuntimeException {

    public ErrorDeAgenda(String mensaje) {
        super(mensaje);
    }
}

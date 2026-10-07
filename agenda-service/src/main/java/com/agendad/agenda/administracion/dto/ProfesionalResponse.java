package com.agendad.agenda.administracion.dto;

import java.util.UUID;

/**
 * Un profesional activo del negocio, tal como lo necesita el panel de
 * recepción para pedir la agenda de cada uno ({@code GET /v1/profesionales}).
 */
public record ProfesionalResponse(UUID id, String nombre) {
}

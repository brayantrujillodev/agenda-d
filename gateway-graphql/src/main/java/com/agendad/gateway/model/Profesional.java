package com.agendad.gateway.model;

import java.util.List;

/**
 * Tipo {@code Profesional}. El campo {@code agenda(fecha)} no vive aquí: lo
 * resuelve {@code PanelController#agendaDelProfesional}, que llama a
 * agenda-service solo si la consulta lo pide. {@code zonaHoraria} no está en
 * el esquema: sirve para saber qué es "hoy" cuando se omite la fecha.
 */
public record Profesional(String id, String nombre, boolean activo,
                          List<Servicio> servicios, List<HorarioAtencion> horarios, String zonaHoraria) {
}

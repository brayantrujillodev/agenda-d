package com.agendad.gateway.model;

import java.util.List;

public record Profesional(String id, String nombre, boolean activo,
                          List<Servicio> servicios, List<HorarioAtencion> horarios) {
    public List<com.agendad.gateway.model.Cita> agenda(java.time.LocalDate fecha) {
        return List.of();
    }
}
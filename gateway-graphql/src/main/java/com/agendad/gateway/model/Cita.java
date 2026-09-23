package com.agendad.gateway.model;

import java.time.Instant;

public record Cita(String id, Instant inicio, Instant fin, String horaLocal,
                   Servicio servicio, Profesional profesional, String clienteNombre,
                   String clienteCelular, String estado) {
}
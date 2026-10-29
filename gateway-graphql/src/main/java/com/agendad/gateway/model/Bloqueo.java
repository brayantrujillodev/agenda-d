package com.agendad.gateway.model;

import java.time.Instant;

public record Bloqueo(String id, Instant inicio, Instant fin, String motivo) {
}

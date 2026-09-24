package com.agendad.analitica.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Deduplicación de eventos: Kafka entrega "al menos una vez". */
@Entity
@Table(name = "evento_procesado", schema = "analitica")
public class EventoProcesado {

    @Id
    private UUID eventoId;

    @Column(nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }

    public EventoProcesado(UUID eventoId, Instant procesadoEn) {
        this.eventoId = eventoId;
        this.procesadoEn = procesadoEn;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}

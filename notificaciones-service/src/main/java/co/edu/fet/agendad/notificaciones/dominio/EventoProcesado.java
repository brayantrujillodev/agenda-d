package co.edu.fet.agendad.notificaciones.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapea {@code notificaciones.evento_procesado}. Kafka entrega los
 * mensajes "al menos una vez"; esta tabla es lo que evita procesar el
 * mismo {@code eventoId} dos veces (CLAUDE.md, convención de
 * consumidores Kafka).
 */
@Entity
@Table(name = "evento_procesado", schema = "notificaciones")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
        // Requerido por JPA
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

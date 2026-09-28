package co.edu.fet.agendad.notificaciones.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapea {@code notificaciones.mensaje_fallido}
 * (db/V2__mensajes_fallidos.sql). Un mensaje que agotó los tres
 * reintentos y terminó en {@code citas.dlq} — docs/TAREAS.md #16.
 */
@Entity
@Table(name = "mensaje_fallido", schema = "notificaciones")
public class MensajeFallido {

    @Id
    private UUID id;

    @Column(name = "topico_origen", nullable = false)
    private String topicoOrigen;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    private String motivo;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "fallido_en", nullable = false)
    private Instant fallidoEn;

    protected MensajeFallido() {
        // Requerido por JPA
    }

    public MensajeFallido(UUID id, String topicoOrigen, String payload, String motivo, int intentos) {
        this.id = id;
        this.topicoOrigen = topicoOrigen;
        this.payload = payload;
        this.motivo = motivo;
        this.intentos = intentos;
        this.fallidoEn = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTopicoOrigen() {
        return topicoOrigen;
    }

    public String getPayload() {
        return payload;
    }

    public String getMotivo() {
        return motivo;
    }

    public int getIntentos() {
        return intentos;
    }

    public Instant getFallidoEn() {
        return fallidoEn;
    }
}

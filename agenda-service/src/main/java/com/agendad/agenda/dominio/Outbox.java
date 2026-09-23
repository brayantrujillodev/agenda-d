package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Bandeja de salida (patrón outbox). El caso de uso de reserva inserta la
 * cita y una fila aquí en la MISMA transacción. Un relay periódico
 * ({@code com.agendad.agenda.outbox.OutboxRelay}) lee lo pendiente, lo
 * publica en Kafka y lo marca como enviado.
 *
 * <p>Nunca se llama a Kafka desde el caso de uso: si el bus está caído, la
 * cita igual se guarda y el evento sale cuando el bus vuelve (CLAUDE.md,
 * regla 2).
 */
@Entity
@Table(name = "outbox")
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negocio_id", nullable = false)
    private UUID negocioId;

    /** Tópico destino: {@code citas.reservadas}, {@code citas.canceladas}, {@code citas.estado}. */
    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    /** Siempre {@code profesionalId}: garantiza el orden por profesional en Kafka. */
    @Column(name = "clave_particion", nullable = false)
    private String claveParticion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    protected Outbox() {
    }

    public Outbox(String tipoEvento, String claveParticion, UUID negocioId, String payload) {
        this.tipoEvento = tipoEvento;
        this.claveParticion = claveParticion;
        this.negocioId = negocioId;
        this.payload = payload;
        this.creadoEn = Instant.now();
        this.intentos = 0;
    }

    public void marcarEnviado(Instant cuando) {
        this.enviadoEn = cuando;
    }

    public void registrarIntentoFallido() {
        this.intentos++;
    }

    public Long getId() {
        return id;
    }

    public UUID getNegocioId() {
        return negocioId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getClaveParticion() {
        return claveParticion;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public int getIntentos() {
        return intentos;
    }
}

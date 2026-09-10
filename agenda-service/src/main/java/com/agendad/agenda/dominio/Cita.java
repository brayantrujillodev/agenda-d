package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Cita agendada. El corazón del sistema.
 *
 * La ausencia de solapamiento la garantiza la restricción {@code cita_sin_solape}
 * de PostgreSQL, no este código. La creación de citas llega en feature/3.
 */
@Entity
@Table(name = "cita")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "negocio_id")
    private UUID negocioId;

    @Column(name = "servicio_id")
    private UUID servicioId;

    @Column(name = "profesional_id")
    private UUID profesionalId;

    private Instant inicio;

    private Instant fin;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "cliente_celular")
    private String clienteCelular;

    @Enumerated(EnumType.STRING)
    private EstadoCita estado;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "creada_en")
    private Instant creadaEn;

    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    protected Cita() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getNegocioId() {
        return negocioId;
    }

    public UUID getServicioId() {
        return servicioId;
    }

    public UUID getProfesionalId() {
        return profesionalId;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFin() {
        return fin;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteCelular() {
        return clienteCelular;
    }

    public EstadoCita getEstado() {
        return estado;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public Instant getActualizadaEn() {
        return actualizadaEn;
    }
}

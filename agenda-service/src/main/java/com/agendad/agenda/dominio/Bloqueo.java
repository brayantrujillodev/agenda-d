package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Excepción puntual a la agenda de un profesional (vacaciones, incapacidad, festivo). En UTC. */
@Entity
@Table(name = "bloqueo")
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "negocio_id")
    private UUID negocioId;

    @Column(name = "profesional_id")
    private UUID profesionalId;

    private Instant inicio;

    private Instant fin;

    private String motivo;

    protected Bloqueo() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getNegocioId() {
        return negocioId;
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

    public String getMotivo() {
        return motivo;
    }
}

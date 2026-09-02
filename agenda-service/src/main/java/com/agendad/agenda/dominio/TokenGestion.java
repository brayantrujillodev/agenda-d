package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Token aleatorio y no enumerable que deja al cliente operar su cita sin
 * tener cuenta (RNF-09). Se entrega en el enlace de gestión y viaja en el
 * evento {@code citas.reservadas}.
 */
@Entity
@Table(name = "token_gestion")
public class TokenGestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cita_id", nullable = false)
    private UUID citaId;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    protected TokenGestion() {
    }

    public TokenGestion(UUID citaId, String token, Instant expiraEn) {
        this.citaId = citaId;
        this.token = token;
        this.expiraEn = expiraEn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCitaId() {
        return citaId;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }
}

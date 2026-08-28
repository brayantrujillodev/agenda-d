package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Negocio de servicios. El {@code slugPublico} resuelve el contexto en las rutas públicas. */
@Entity
@Table(name = "negocio")
public class Negocio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    @Column(name = "slug_publico")
    private String slugPublico;

    @Column(name = "zona_horaria")
    private String zonaHoraria;

    @Column(name = "canal_primario")
    private String canalPrimario;

    @Column(name = "canal_respaldo")
    private String canalRespaldo;

    @Column(name = "creado_en")
    private Instant creadoEn;

    protected Negocio() {
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getSlugPublico() {
        return slugPublico;
    }

    public String getZonaHoraria() {
        return zonaHoraria;
    }

    public String getCanalPrimario() {
        return canalPrimario;
    }

    public String getCanalRespaldo() {
        return canalRespaldo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}

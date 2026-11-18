package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Servicio que ofrece un negocio. La duración fija el tamaño del cupo. */
@Entity
@Table(name = "servicio")
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "negocio_id")
    private UUID negocioId;

    private String nombre;

    @Column(name = "duracion_min")
    private int duracionMin;

    private BigDecimal precio;

    private boolean activo;

    protected Servicio() {
    }

    /** Servicio nuevo del negocio. Nace activo. */
    public Servicio(UUID negocioId, String nombre, int duracionMin, BigDecimal precio) {
        this.negocioId = negocioId;
        this.nombre = nombre;
        this.duracionMin = duracionMin;
        this.precio = precio;
        this.activo = true;
    }

    /** Cambiar la duración no mueve citas ya reservadas: aplica a las reservas nuevas. */
    public void actualizar(String nombre, int duracionMin, BigDecimal precio) {
        this.nombre = nombre;
        this.duracionMin = duracionMin;
        this.precio = precio;
    }

    /** Borrado lógico: las citas que ya lo usan conservan la referencia. */
    public void desactivar() {
        this.activo = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNegocioId() {
        return negocioId;
    }

    public String getNombre() {
        return nombre;
    }

    public int getDuracionMin() {
        return duracionMin;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public boolean isActivo() {
        return activo;
    }
}

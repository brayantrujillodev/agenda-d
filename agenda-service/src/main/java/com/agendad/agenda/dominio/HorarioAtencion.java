package com.agendad.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Franja semanal recurrente de atención, en HORA LOCAL del negocio.
 * Se convierte a UTC al calcular la disponibilidad.
 */
@Entity
@Table(name = "horario_atencion")
public class HorarioAtencion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "negocio_id")
    private UUID negocioId;

    @Column(name = "profesional_id")
    private UUID profesionalId;

    /** 1 = lunes ... 7 = domingo (ISO-8601). */
    @Column(name = "dia_semana")
    private short diaSemana;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fin")
    private LocalTime horaFin;

    protected HorarioAtencion() {
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

    public short getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }
}

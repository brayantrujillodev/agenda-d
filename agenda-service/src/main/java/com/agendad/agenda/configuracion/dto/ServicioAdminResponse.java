package com.agendad.agenda.configuracion.dto;

import com.agendad.agenda.dominio.Servicio;
import java.math.BigDecimal;
import java.util.UUID;

/** Servicio visto por el propio negocio. Contrato: {@code ServicioAdmin}. */
public record ServicioAdminResponse(UUID id, String nombre, int duracionMin, BigDecimal precio, boolean activo) {

    public static ServicioAdminResponse de(Servicio s) {
        return new ServicioAdminResponse(s.getId(), s.getNombre(), s.getDuracionMin(), s.getPrecio(), s.isActivo());
    }
}

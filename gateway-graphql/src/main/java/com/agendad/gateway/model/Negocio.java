package com.agendad.gateway.model;

import java.util.List;

public record Negocio(String id, String nombre, String slugPublico, String zonaHoraria,
                      List<Servicio> servicios, List<Profesional> profesionales) {
}
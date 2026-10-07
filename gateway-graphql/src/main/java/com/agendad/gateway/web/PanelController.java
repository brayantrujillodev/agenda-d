package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.AnaliticaClient;
import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.Metricas;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.PanelRecepcion;
import com.agendad.gateway.model.Profesional;
import com.agendad.gateway.model.Servicio;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;

@Controller
public class PanelController {

    private final AgendaClient agendaClient;
    private final AnaliticaClient analiticaClient;

    public PanelController(AgendaClient agendaClient, AnaliticaClient analiticaClient) {
        this.agendaClient = agendaClient;
        this.analiticaClient = analiticaClient;
    }

    @QueryMapping
    public PanelRecepcion panelRecepcion(@Argument LocalDate fecha) {
        LocalDate fechaSolicitada = fecha != null ? fecha : LocalDate.now();
        List<Servicio> servicios = agendaClient.servicios();
        List<Profesional> profesionales = agendaClient.profesionales(servicios);
        List<Cita> citas = agendaClient.citas(fechaSolicitada, servicios, profesionales);
        Negocio negocio = agendaClient.negocio(servicios, profesionales);
        Metricas metricasDelMes = analiticaClient.metricas(fechaSolicitada.withDayOfMonth(1), fechaSolicitada);
        return new PanelRecepcion(fechaSolicitada, negocio, citas, metricasDelMes);
    }

    @QueryMapping
    public Negocio negocio() {
        List<Servicio> servicios = agendaClient.servicios();
        return agendaClient.negocio(servicios, agendaClient.profesionales(servicios));
    }

    @QueryMapping
    public List<Cita> agenda(@Argument String profesionalId, @Argument LocalDate fecha) {
        List<Servicio> servicios = agendaClient.servicios();
        List<Profesional> delProfesional = agendaClient.profesionales(servicios).stream()
                .filter(p -> p.id().equals(profesionalId))
                .toList();
        return agendaClient.citas(fecha, servicios, delProfesional);
    }

    @QueryMapping
    public Metricas metricas(@Argument LocalDate desde, @Argument LocalDate hasta) {
        return analiticaClient.metricas(desde, hasta);
    }

    @MutationMapping
    public Cita registrarAsistencia(@Argument String citaId, @Argument String estado) {
        return agendaClient.registrarAsistencia(citaId, estado, agendaClient.servicios());
    }

    /** {@code motivo} se acepta por contrato pero hoy no se guarda: el evento citas.canceladas no lo lleva. */
    @MutationMapping
    public Cita cancelarCita(@Argument String citaId, @Argument String motivo) {
        return agendaClient.cancelar(citaId, agendaClient.servicios());
    }
}

package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.Metricas;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.PanelRecepcion;
import com.agendad.gateway.model.Servicio;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;

@Controller
public class PanelController {

    private final AgendaClient agendaClient;

    public PanelController(AgendaClient agendaClient) {
        this.agendaClient = agendaClient;
    }

    @QueryMapping
    public PanelRecepcion panelRecepcion(@Argument LocalDate fecha) {
        LocalDate fechaSolicitada = fecha != null ? fecha : LocalDate.now();
        List<Servicio> servicios = agendaClient.servicios();
        List<Cita> citas = agendaClient.citas(fechaSolicitada, servicios);
        Negocio negocio = agendaClient.negocio(servicios);
        return new PanelRecepcion(fechaSolicitada, negocio, citas,
                metricasSinAnalitica(fechaSolicitada, servicios));
    }

    @QueryMapping
    public Negocio negocio() {
        List<Servicio> servicios = agendaClient.servicios();
        return agendaClient.negocio(servicios);
    }

    @QueryMapping
    public List<Cita> agenda(@Argument String profesionalId, @Argument LocalDate fecha) {
        List<Servicio> servicios = agendaClient.servicios();
        return agendaClient.citas(fecha, servicios).stream()
                .filter(cita -> cita.profesional().id().equals(profesionalId))
                .toList();
    }

    @QueryMapping
    public Metricas metricas(@Argument LocalDate desde, @Argument LocalDate hasta) {
        return metricasSinAnalitica(desde, List.of());
    }

    private Metricas metricasSinAnalitica(LocalDate fecha, List<Servicio> servicios) {
        // TODO: conectar analitica-service cuando exista.
        return new Metricas(fecha.withDayOfMonth(1), fecha, 0, 0, 0, 0,
                0.0, 0.0, List.of());
    }
}
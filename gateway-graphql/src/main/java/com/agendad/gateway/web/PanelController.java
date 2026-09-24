package com.agendad.gateway.web;

import com.agendad.gateway.client.AgendaClient;
import com.agendad.gateway.client.AnaliticaClient;
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
    private final AnaliticaClient analiticaClient;

    public PanelController(AgendaClient agendaClient, AnaliticaClient analiticaClient) {
        this.agendaClient = agendaClient;
        this.analiticaClient = analiticaClient;
    }

    @QueryMapping
    public PanelRecepcion panelRecepcion(@Argument LocalDate fecha) {
        LocalDate fechaSolicitada = fecha != null ? fecha : LocalDate.now();
        List<Servicio> servicios = agendaClient.servicios();
        List<Cita> citas = agendaClient.citas(fechaSolicitada, servicios);
        Negocio negocio = agendaClient.negocio(servicios);
        Metricas metricasDelMes = analiticaClient.metricas(fechaSolicitada.withDayOfMonth(1), fechaSolicitada);
        return new PanelRecepcion(fechaSolicitada, negocio, citas, metricasDelMes);
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
        return analiticaClient.metricas(desde, hasta);
    }
}
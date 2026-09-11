package com.agendad.gateway.client;

import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.HorarioAtencion;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.Profesional;
import com.agendad.gateway.model.Servicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AgendaClient {

    private static final String SERVICIO_CORTE_ID = "22222222-2222-2222-2222-222222222222";
    private static final String SERVICIO_BARBA_ID = "22222222-2222-2222-2222-222222222223";

    private final RestClient restClient;
    private final String agendaUrl;
    private final String negocioSlug;
    private final String negocioId;
    private final String profesionalId;

    public AgendaClient(RestClient restClient,
                        @Value("${AGENDAD_AGENDA_URL:http://localhost:8081}") String agendaUrl,
                        @Value("${AGENDAD_NEGOCIO_SLUG:barberia-el-corte}") String negocioSlug,
                        @Value("${AGENDAD_NEGOCIO_ID:11111111-1111-1111-1111-111111111111}") String negocioId,
                        @Value("${AGENDAD_PROFESIONAL_ID:33333333-3333-3333-3333-333333333333}") String profesionalId) {
        this.restClient = restClient;
        this.agendaUrl = agendaUrl;
        this.negocioSlug = negocioSlug;
        this.negocioId = negocioId;
        this.profesionalId = profesionalId;
    }

    public List<Servicio> servicios() {
        try {
            List<ServicioResponse> response = restClient.get()
                    .uri(agendaUrl + "/v1/publico/{slug}/servicios", negocioSlug)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response != null && !response.isEmpty()) {
                return response.stream().map(this::toServicio).toList();
            }
        } catch (RestClientException ignored) {
            // El contrato permite desarrollar el gateway antes que agenda-service.
        }
        return serviciosDeEjemplo();
    }

    public List<Cita> citas(LocalDate fecha, List<Servicio> servicios) {
        try {
            List<CitaResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(agendaUrl + "/v1/agenda/{profesionalId}")
                            .queryParam("fecha", fecha)
                            .build(profesionalId))
                    .header("X-Negocio-Id", negocioId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response != null) {
                return response.stream().map(item -> toCita(item, servicios)).toList();
            }
        } catch (RestClientException ignored) {
            // El contrato permite desarrollar el gateway antes que agenda-service.
        }
        return citasDeEjemplo(fecha, servicios);
    }

    public Negocio negocio(List<Servicio> servicios) {
        Profesional profesional = new Profesional(
                profesionalId, "Laura", true, servicios,
                List.of(new HorarioAtencion(1, "08:00", "18:00"),
                        new HorarioAtencion(2, "08:00", "18:00")));
        return new Negocio(negocioId, "El Corte", negocioSlug, "America/Bogota",
                servicios, List.of(profesional));
    }

    private Cita toCita(CitaResponse item, List<Servicio> servicios) {
        Servicio servicio = servicios.stream()
                .filter(candidate -> candidate.nombre().equals(item.servicioNombre()))
                .findFirst()
                .orElse(servicios.getFirst());
        Profesional profesional = new Profesional(profesionalId, item.profesionalNombre(), true,
                servicios, List.of());
        return new Cita(item.id(), item.inicio(), item.fin(), item.horaLocal(), servicio,
                profesional, item.clienteNombre(), item.clienteCelular(), item.estado());
    }

    private List<Servicio> serviciosDeEjemplo() {
        return List.of(
                new Servicio(SERVICIO_CORTE_ID, "Corte de cabello", 60, new BigDecimal("25000"), true),
                new Servicio(SERVICIO_BARBA_ID, "Barba", 30, new BigDecimal("15000"), true));
    }

    private List<Cita> citasDeEjemplo(LocalDate fecha, List<Servicio> servicios) {
        Servicio servicio = servicios.getFirst();
        var inicio = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(8 * 3600L);
        var fin = inicio.plusSeconds(3600L);
        Profesional profesional = new Profesional(profesionalId, "Laura", true, servicios, List.of());
        return List.of(new Cita(UUID.nameUUIDFromBytes((fecha + "-cita").getBytes()).toString(),
                inicio, fin, "08:00", servicio, profesional, "Juan Pérez", "300****567", "CONFIRMADA"));
    }

    private Servicio toServicio(ServicioResponse item) {
        return new Servicio(item.id(), item.nombre(), item.duracionMin(), item.precio(), true);
    }

    private record ServicioResponse(String id, String nombre, int duracionMin, BigDecimal precio) {}

    private record CitaResponse(String id, java.time.Instant inicio, java.time.Instant fin,
                                String horaLocal, String servicioNombre, String profesionalNombre,
                                String clienteNombre, String clienteCelular, String estado) {}
}
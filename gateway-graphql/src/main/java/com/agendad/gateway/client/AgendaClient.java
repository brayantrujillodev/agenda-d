package com.agendad.gateway.client;

import com.agendad.gateway.model.Cita;
import com.agendad.gateway.model.HorarioAtencion;
import com.agendad.gateway.model.Negocio;
import com.agendad.gateway.model.Profesional;
import com.agendad.gateway.model.Servicio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class AgendaClient {

    private static final Logger log = LoggerFactory.getLogger(AgendaClient.class);

    private static final String SERVICIO_CORTE_ID = "22222222-2222-2222-2222-222222222222";
    private static final String SERVICIO_BARBA_ID = "22222222-2222-2222-2222-222222222223";
    private static final String PROFESIONAL_EJEMPLO_ID = "33333333-3333-3333-3333-333333333333";

    private final RestClient restClient;
    private final String agendaUrl;
    private final String negocioSlug;
    private final String negocioId;

    public AgendaClient(RestClient restClient,
                        @Value("${AGENDAD_AGENDA_URL:http://localhost:8081}") String agendaUrl,
                        @Value("${AGENDAD_NEGOCIO_SLUG:barberia-el-corte}") String negocioSlug,
                        @Value("${AGENDAD_NEGOCIO_ID:11111111-1111-1111-1111-111111111111}") String negocioId) {
        this.restClient = restClient;
        this.agendaUrl = agendaUrl;
        this.negocioSlug = negocioSlug;
        this.negocioId = negocioId;
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
        } catch (RestClientException e) {
            log.warn("No se pudo consultar servicios en agenda-service, usando datos de ejemplo: {}", e.getMessage());
        }
        return serviciosDeEjemplo();
    }

    /** Profesionales activos del negocio. Si agenda-service no responde, uno de ejemplo. */
    public List<Profesional> profesionales(List<Servicio> servicios) {
        try {
            List<ProfesionalResponse> response = restClient.get()
                    .uri(agendaUrl + "/v1/profesionales")
                    .header("X-Negocio-Id", negocioId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response != null) {
                return response.stream()
                        .map(p -> new Profesional(p.id(), p.nombre(), true, servicios, List.of()))
                        .toList();
            }
        } catch (RestClientException e) {
            log.warn("No se pudo consultar profesionales en agenda-service, usando datos de ejemplo: {}",
                    e.getMessage());
        }
        return List.of(profesionalDeEjemplo(servicios));
    }

    /** Citas del día de TODOS los profesionales, ordenadas por hora de inicio. */
    public List<Cita> citas(LocalDate fecha, List<Servicio> servicios, List<Profesional> profesionales) {
        try {
            List<Cita> todas = new ArrayList<>();
            for (Profesional profesional : profesionales) {
                List<CitaResponse> response = restClient.get()
                        .uri(agendaUrl + "/v1/agenda/{profesionalId}?fecha={fecha}", profesional.id(), fecha)
                        .header("X-Negocio-Id", negocioId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                if (response != null) {
                    response.forEach(item -> todas.add(toCita(item, servicios, profesional)));
                }
            }
            todas.sort(Comparator.comparing(Cita::inicio));
            return todas;
        } catch (RestClientException e) {
            log.warn("No se pudo consultar la agenda en agenda-service, usando datos de ejemplo: {}",
                    e.getMessage());
        }
        return citasDeEjemplo(fecha, servicios);
    }

    public Negocio negocio(List<Servicio> servicios, List<Profesional> profesionales) {
        return new Negocio(negocioId, "El Corte", negocioSlug, "America/Bogota",
                servicios, profesionales);
    }

    /**
     * Registra ATENDIDA o NO_ASISTIO y devuelve la cita actualizada. A
     * diferencia de las consultas, aquí no hay datos de ejemplo: una escritura
     * que no llegó a agenda-service tiene que verse como error en el panel.
     */
    public Cita registrarAsistencia(String citaId, String estado, List<Servicio> servicios) {
        escribir(() -> restClient.patch()
                .uri(agendaUrl + "/v1/citas/{id}/estado", citaId)
                .header("X-Negocio-Id", negocioId)
                .body(Map.of("estado", estado))
                .retrieve()
                .toBodilessEntity());
        return cita(citaId, servicios);
    }

    /** Cancela desde el negocio (canceladaPor = NEGOCIO) y devuelve la cita actualizada. */
    public Cita cancelar(String citaId, List<Servicio> servicios) {
        escribir(() -> restClient.post()
                .uri(agendaUrl + "/v1/citas/{id}/cancelacion", citaId)
                .header("X-Negocio-Id", negocioId)
                .retrieve()
                .toBodilessEntity());
        return cita(citaId, servicios);
    }

    private Cita cita(String citaId, List<Servicio> servicios) {
        CitaResponse item = escribir(() -> restClient.get()
                .uri(agendaUrl + "/v1/citas/{id}", citaId)
                .header("X-Negocio-Id", negocioId)
                .retrieve()
                .body(CitaResponse.class));
        // El detalle de agenda-service trae el nombre del profesional, no su id:
        // se resuelve contra la lista del negocio.
        Profesional profesional = profesionales(servicios).stream()
                .filter(p -> p.nombre().equals(item.profesionalNombre()))
                .findFirst()
                .orElseGet(() -> new Profesional("desconocido", item.profesionalNombre(), true,
                        servicios, List.of()));
        return toCita(item, servicios, profesional);
    }

    /**
     * Traduce un error de agenda-service a {@link ErrorDeAgenda} con su
     * mensaje en español, que el resolver de GraphQL entrega tal cual al panel.
     */
    private <T> T escribir(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (RestClientResponseException e) {
            String mensaje = null;
            try {
                Map<?, ?> cuerpo = e.getResponseBodyAs(Map.class);
                if (cuerpo != null && cuerpo.get("mensaje") instanceof String m) {
                    mensaje = m;
                }
            } catch (RuntimeException ignorada) {
                // cuerpo no JSON: se usa el mensaje genérico
            }
            throw new ErrorDeAgenda(mensaje != null ? mensaje : "La agenda rechazó la operación. Intenta de nuevo.");
        } catch (RestClientException e) {
            log.warn("agenda-service no respondió a una escritura: {}", e.getMessage());
            throw new ErrorDeAgenda("No pudimos contactar la agenda. Revisa la conexión e intenta de nuevo.");
        }
    }

    private Cita toCita(CitaResponse item, List<Servicio> servicios, Profesional profesional) {
        Servicio servicio = servicios.stream()
                .filter(candidate -> candidate.nombre().equals(item.servicioNombre()))
                .findFirst()
                .orElse(servicios.getFirst());
        return new Cita(item.id(), item.inicio(), item.fin(), item.horaLocal(), servicio,
                profesional, item.clienteNombre(), item.clienteCelular(), item.estado());
    }

    private List<Servicio> serviciosDeEjemplo() {
        return List.of(
                new Servicio(SERVICIO_CORTE_ID, "Corte de cabello", 60, new BigDecimal("25000"), true),
                new Servicio(SERVICIO_BARBA_ID, "Barba", 30, new BigDecimal("15000"), true));
    }

    private Profesional profesionalDeEjemplo(List<Servicio> servicios) {
        return new Profesional(PROFESIONAL_EJEMPLO_ID, "Laura", true, servicios,
                List.of(new HorarioAtencion(1, "08:00", "18:00"),
                        new HorarioAtencion(2, "08:00", "18:00")));
    }

    private List<Cita> citasDeEjemplo(LocalDate fecha, List<Servicio> servicios) {
        Servicio servicio = servicios.getFirst();
        var inicio = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(8 * 3600L);
        var fin = inicio.plusSeconds(3600L);
        return List.of(new Cita(UUID.nameUUIDFromBytes((fecha + "-cita").getBytes()).toString(),
                inicio, fin, "08:00", servicio, profesionalDeEjemplo(servicios),
                "Juan Pérez", "300****567", "CONFIRMADA"));
    }

    private Servicio toServicio(ServicioResponse item) {
        return new Servicio(item.id(), item.nombre(), item.duracionMin(), item.precio(), true);
    }

    private record ServicioResponse(String id, String nombre, int duracionMin, BigDecimal precio) {}

    private record ProfesionalResponse(String id, String nombre) {}

    private record CitaResponse(String id, Instant inicio, Instant fin,
                                String horaLocal, String servicioNombre, String profesionalNombre,
                                String clienteNombre, String clienteCelular, String estado) {}
}

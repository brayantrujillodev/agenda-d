package com.agendad.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.agendad.gateway.config.RestClientConfig;
import com.agendad.gateway.error.ErrorGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Lo que de verdad viaja por HTTP entre el gateway y los servicios: la
 * cabecera del negocio, y cómo se traduce cada respuesta de error.
 */
class ClientesHttpTest {

    private static final UUID NEGOCIO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LAURA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockRestServiceServer servidor;
    private AgendaClient agenda;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        agenda = new AgendaClient(RestClientConfig.configurar(builder, "http://agenda", "el servicio de agenda", objectMapper).build());
    }

    @Test
    void reenviaXNegocioIdYCorrelationIdEnCadaLlamada() {
        servidor.expect(requestTo("http://agenda/v1/agenda/" + LAURA + "?fecha=2026-11-03"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Negocio-Id", NEGOCIO.toString()))
                .andExpect(header("X-Correlation-Id", "corr-1"))
                .andRespond(withSuccess("""
                        [{"id":"c-1","inicio":"2026-11-03T13:00:00Z","fin":"2026-11-03T14:00:00Z","horaLocal":"08:00",
                          "servicioId":"s","servicioNombre":"Corte","profesionalId":"p","profesionalNombre":"Laura",
                          "clienteNombre":"Juan Pérez","clienteCelular":"3001234567","estado":"CONFIRMADA"}]""",
                        MediaType.APPLICATION_JSON));

        var citas = agenda.agenda(NEGOCIO, "corr-1", LAURA, LocalDate.parse("2026-11-03"));

        assertThat(citas).singleElement().satisfies(c -> assertThat(c.horaLocal()).isEqualTo("08:00"));
        servidor.verify();
    }

    @Test
    void un404DelServicioConservaSuCodigoYMensaje() {
        servidor.expect(requestTo("http://agenda/v1/citas/" + LAURA))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"codigo\":\"NO_ENCONTRADO\",\"mensaje\":\"No encontramos esa cita en este negocio.\"}"));

        assertThatThrownBy(() -> agenda.cita(NEGOCIO, null, LAURA))
                .isInstanceOfSatisfying(ErrorGateway.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("NO_ENCONTRADO");
                    assertThat(e.getMessage()).isEqualTo("No encontramos esa cita en este negocio.");
                    assertThat(e.getTipo()).isEqualTo(ErrorType.NOT_FOUND);
                });
    }

    @Test
    void un409SeExponeComoBadRequestConSuCodigo() {
        servidor.expect(requestTo("http://agenda/v1/bloqueos"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"codigo\":\"CITAS_EN_RANGO\",\"mensaje\":\"Hay 1 cita confirmada en ese rango.\"}"));

        assertThatThrownBy(() -> agenda.crearBloqueo(NEGOCIO, null, LAURA,
                java.time.Instant.parse("2026-11-03T13:00:00Z"), java.time.Instant.parse("2026-11-03T15:00:00Z"), null))
                .isInstanceOfSatisfying(ErrorGateway.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("CITAS_EN_RANGO");
                    assertThat(e.getTipo()).isEqualTo(ErrorType.BAD_REQUEST);
                });
    }

    @Test
    void un500SinCuerpoDelContratoNoReenviaTextoTecnico() {
        servidor.expect(requestTo("http://agenda/v1/negocio"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML)
                        .body("<html>nginx 502 Bad Gateway</html>"));

        assertThatThrownBy(() -> agenda.negocio(NEGOCIO, null))
                .isInstanceOfSatisfying(ErrorGateway.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("ERROR_INTERNO");
                    assertThat(e.getMessage()).doesNotContain("nginx").contains("el servicio de agenda");
                    assertThat(e.getTipo()).isEqualTo(ErrorType.INTERNAL_ERROR);
                });
    }

    @Test
    void unServicioQueNoRespondeEsServicioNoDisponible() {
        // Puerto 1: nada escucha ahí; la conexión se rechaza de inmediato.
        AnaliticaClient caido = new AnaliticaClient(RestClientConfig.configurar(
                RestClient.builder().requestFactory(RestClientConfig.fabrica(500, 500)),
                "http://127.0.0.1:1", "el servicio de métricas", objectMapper).build());

        assertThatThrownBy(() -> caido.metricas(NEGOCIO, null, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03")))
                .isInstanceOfSatisfying(ErrorGateway.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("SERVICIO_NO_DISPONIBLE");
                    assertThat(e.getMessage()).contains("el servicio de métricas");
                });
    }

    /**
     * Regresión: con SimpleClientHttpRequestFactory (HttpURLConnection) el
     * PATCH de registrarAsistencia fallaba con "Invalid HTTP method: PATCH".
     * MockRestServiceServer reemplaza la fábrica y no lo detecta: aquí va un
     * servidor HTTP de verdad con la misma fábrica que usa producción.
     */
    @Test
    void laFabricaDeProduccionSabeHacerPatch() throws Exception {
        HttpServer servidorReal = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> metodo = new AtomicReference<>();
        servidorReal.createContext("/v1/citas", intercambio -> {
            metodo.set(intercambio.getRequestMethod());
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidorReal.start();
        try {
            String url = "http://127.0.0.1:" + servidorReal.getAddress().getPort();
            AgendaClient real = new AgendaClient(RestClientConfig.configurar(
                    RestClient.builder().requestFactory(RestClientConfig.fabrica(1000, 1000)),
                    url, "el servicio de agenda", objectMapper).build());

            real.registrarAsistencia(NEGOCIO, null, LAURA, "ATENDIDA");

            assertThat(metodo.get()).isEqualTo("PATCH");
        } finally {
            servidorReal.stop(0);
        }
    }
}

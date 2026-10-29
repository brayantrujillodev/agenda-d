package com.agendad.gateway.config;

import com.agendad.gateway.client.TraductorErroresHttp;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Un {@link RestClient} por servicio interno, con su URL base, timeouts y la
 * traducción de errores del contrato. Sin timeouts, un servicio colgado
 * colgaría también al gateway y al panel.
 */
@Configuration
public class RestClientConfig {

    private final int timeoutConexionMs;
    private final int timeoutLecturaMs;

    public RestClientConfig(@Value("${agendad.http.timeout-conexion-ms}") int timeoutConexionMs,
                            @Value("${agendad.http.timeout-lectura-ms}") int timeoutLecturaMs) {
        this.timeoutConexionMs = timeoutConexionMs;
        this.timeoutLecturaMs = timeoutLecturaMs;
    }

    @Bean
    RestClient agendaRestClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                @Value("${agendad.servicios.agenda-url}") String url) {
        return configurar(builder.clone().requestFactory(fabrica(timeoutConexionMs, timeoutLecturaMs)),
                url, "el servicio de agenda", objectMapper).build();
    }

    @Bean
    RestClient analiticaRestClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                   @Value("${agendad.servicios.analitica-url}") String url) {
        return configurar(builder.clone().requestFactory(fabrica(timeoutConexionMs, timeoutLecturaMs)),
                url, "el servicio de métricas", objectMapper).build();
    }

    /**
     * Separado de la fábrica de conexiones para que las pruebas lo apliquen
     * sobre un builder atado a {@code MockRestServiceServer}.
     */
    public static RestClient.Builder configurar(RestClient.Builder builder, String url, String servicio,
                                                ObjectMapper objectMapper) {
        return builder
                .baseUrl(url)
                .defaultStatusHandler(HttpStatusCode::isError, new TraductorErroresHttp(servicio, objectMapper));
    }

    /**
     * El cliente HTTP del JDK. No el de HttpURLConnection
     * ({@code SimpleClientHttpRequestFactory}): ese no admite PATCH, y
     * {@code PATCH /v1/citas/{id}/estado} es como se registra la asistencia.
     */
    public static ClientHttpRequestFactory fabrica(int timeoutConexionMs, int timeoutLecturaMs) {
        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutConexionMs))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutLecturaMs));
        return fabrica;
    }
}

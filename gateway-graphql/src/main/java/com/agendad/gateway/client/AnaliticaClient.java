package com.agendad.gateway.client;

import com.agendad.gateway.model.Metricas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.List;

@Service
public class AnaliticaClient {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaClient.class);

    private final RestClient restClient;
    private final String analiticaUrl;
    private final String negocioId;

    public AnaliticaClient(RestClient restClient,
                            @Value("${AGENDAD_ANALITICA_URL:http://localhost:8083}") String analiticaUrl,
                            @Value("${AGENDAD_NEGOCIO_ID:11111111-1111-1111-1111-111111111111}") String negocioId) {
        this.restClient = restClient;
        this.analiticaUrl = analiticaUrl;
        this.negocioId = negocioId;
    }

    public Metricas metricas(LocalDate desde, LocalDate hasta) {
        try {
            MetricasResponse response = restClient.get()
                    .uri(analiticaUrl + "/v1/metricas?desde={desde}&hasta={hasta}", desde, hasta)
                    .header("X-Negocio-Id", negocioId)
                    .retrieve()
                    .body(MetricasResponse.class);
            if (response != null) {
                return new Metricas(response.desde(), response.hasta(), response.totalCitas(),
                        response.atendidas(), response.noAsistio(), response.canceladas(),
                        response.ocupacion(), response.tasaInasistencia(), List.of());
            }
        } catch (RestClientException e) {
            log.warn("No se pudo consultar métricas en analitica-service, usando ceros: {}", e.getMessage());
        }
        return metricasVacias(desde, hasta);
    }

    private Metricas metricasVacias(LocalDate desde, LocalDate hasta) {
        return new Metricas(desde, hasta, 0, 0, 0, 0, 0.0, 0.0, List.of());
    }

    private record MetricasResponse(LocalDate desde, LocalDate hasta, int totalCitas, int atendidas,
                                     int noAsistio, int canceladas, double ocupacion, double tasaInasistencia) {
    }
}

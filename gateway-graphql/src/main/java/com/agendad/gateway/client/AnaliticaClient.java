package com.agendad.gateway.client;

import static com.agendad.gateway.contexto.ContextoNegocio.CABECERA_CORRELACION;
import static com.agendad.gateway.contexto.ContextoNegocio.CABECERA_NEGOCIO;

import com.agendad.gateway.client.dto.MetricasDto;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de analitica-service (contrato:
 * docs/openapi/analitica-service.yaml). Si no responde, el error sube al
 * campo que lo pidió: el gateway no devuelve ceros que parezcan reales.
 */
@Service
public class AnaliticaClient {

    static final String NOMBRE = "el servicio de métricas";

    private final RestClient http;

    public AnaliticaClient(@Qualifier("analiticaRestClient") RestClient http) {
        this.http = http;
    }

    public MetricasDto metricas(UUID negocioId, String correlationId, LocalDate desde, LocalDate hasta) {
        return LlamadaRemota.ejecutar(NOMBRE, () -> http.get()
                .uri("/v1/metricas?desde={desde}&hasta={hasta}", desde, hasta)
                .headers(h -> {
                    h.set(CABECERA_NEGOCIO, negocioId.toString());
                    if (correlationId != null) {
                        h.set(CABECERA_CORRELACION, correlationId);
                    }
                })
                .retrieve()
                .body(MetricasDto.class));
    }
}

package com.agendad.gateway.client;

import com.agendad.gateway.error.ErrorGateway;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Envuelve una llamada HTTP a un servicio interno. Si el servicio no
 * responde (caído, timeout) el resultado es un error explícito, nunca datos
 * de ejemplo: un panel que inventa citas es peor que uno que dice "no pude".
 */
final class LlamadaRemota {

    private static final Logger log = LoggerFactory.getLogger(LlamadaRemota.class);

    private LlamadaRemota() {
    }

    static <T> T ejecutar(String servicio, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ErrorGateway e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Sin respuesta de {}: {}", servicio, e.getMessage());
            throw new ErrorGateway("SERVICIO_NO_DISPONIBLE",
                    "No pudimos comunicarnos con " + servicio + ". Intenta de nuevo en unos segundos.",
                    ErrorType.INTERNAL_ERROR);
        } catch (RestClientException e) {
            log.error("Respuesta inesperada de {}", servicio, e);
            throw new ErrorGateway("ERROR_INTERNO",
                    "Recibimos una respuesta inesperada de " + servicio + ". Intenta de nuevo en un momento.",
                    ErrorType.INTERNAL_ERROR);
        }
    }

    static void ejecutar(String servicio, Runnable llamada) {
        ejecutar(servicio, () -> {
            llamada.run();
            return null;
        });
    }
}

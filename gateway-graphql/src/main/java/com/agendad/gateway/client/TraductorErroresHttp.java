package com.agendad.gateway.client;

import com.agendad.gateway.error.ErrorGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Convierte la respuesta de error de un servicio interno
 * ({@code { "codigo", "mensaje" }}, ver CLAUDE.md) en {@link ErrorGateway}
 * con el mismo código y mensaje: el gateway no reescribe ni inventa nada.
 *
 * <p>Si el cuerpo no trae el formato del contrato (un proxy, un 502 de
 * Docker), el mensaje es genérico: nunca se reenvía texto técnico.
 */
public class TraductorErroresHttp implements RestClient.ResponseSpec.ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(TraductorErroresHttp.class);

    private final String servicio;
    private final ObjectMapper objectMapper;

    /** @param servicio cómo se nombra el servicio en un mensaje al usuario, p. ej. "el servicio de agenda". */
    public TraductorErroresHttp(String servicio, ObjectMapper objectMapper) {
        this.servicio = servicio;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode estado = response.getStatusCode();
        String codigo = null;
        String mensaje = null;
        try {
            JsonNode cuerpo = objectMapper.readTree(response.getBody());
            if (cuerpo != null) {
                codigo = texto(cuerpo, "codigo");
                mensaje = texto(cuerpo, "mensaje");
            }
        } catch (IOException e) {
            // Cuerpo vacío o no JSON: se cae al mensaje genérico de abajo.
        }

        if (estado.is5xxServerError()) {
            log.warn("{} respondió {} a {} {} (codigo {})",
                    servicio, estado.value(), request.getMethod(), request.getURI(), codigo);
        }
        throw new ErrorGateway(
                codigo != null ? codigo : codigoPorDefecto(estado),
                mensaje != null ? mensaje : mensajePorDefecto(estado),
                clasificacion(estado));
    }

    static ErrorType clasificacion(HttpStatusCode estado) {
        if (estado.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorType.NOT_FOUND;
        }
        // GraphQL no tiene un "conflicto": un 409 es una petición que no
        // procede con el estado actual. El detalle va en extensions.codigo.
        if (estado.is4xxClientError()) {
            return ErrorType.BAD_REQUEST;
        }
        return ErrorType.INTERNAL_ERROR;
    }

    private static String codigoPorDefecto(HttpStatusCode estado) {
        if (estado.value() == HttpStatus.NOT_FOUND.value()) {
            return "NO_ENCONTRADO";
        }
        return estado.is4xxClientError() ? "DATOS_INVALIDOS" : "ERROR_INTERNO";
    }

    private String mensajePorDefecto(HttpStatusCode estado) {
        if (estado.value() == HttpStatus.NOT_FOUND.value()) {
            return "No encontramos lo que buscas en " + servicio + ".";
        }
        if (estado.is4xxClientError()) {
            return "Revisa los datos enviados.";
        }
        return "Tuvimos un problema consultando " + servicio + ". Intenta de nuevo en un momento.";
    }

    private static String texto(JsonNode cuerpo, String campo) {
        JsonNode valor = cuerpo.get(campo);
        return valor != null && valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }
}

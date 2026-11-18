package com.agendad.gateway.contexto;

import com.agendad.gateway.error.ErrorGateway;
import graphql.GraphQLContext;
import java.util.UUID;

/**
 * Contexto de la petición GraphQL: el negocio de la cabecera
 * {@code X-Negocio-Id} (CLAUDE.md, regla 4) y el {@code X-Correlation-Id}
 * que se reenvía a los servicios para seguir una operación en los logs.
 * Lo llena {@link ContextoNegocioInterceptor}.
 */
public final class ContextoNegocio {

    public static final String CABECERA_NEGOCIO = "X-Negocio-Id";
    public static final String CABECERA_CORRELACION = "X-Correlation-Id";

    static final String CLAVE_NEGOCIO = "agendad.negocioId";
    static final String CLAVE_CORRELACION = "agendad.correlationId";

    private ContextoNegocio() {
    }

    /** El negocio de la petición. Sin él no hay a quién consultar: es un error del cliente. */
    public static UUID negocioId(GraphQLContext contexto) {
        String valor = contexto.get(CLAVE_NEGOCIO);
        if (valor == null || valor.isBlank()) {
            throw ErrorGateway.datosInvalidos(
                    "Falta la cabecera " + CABECERA_NEGOCIO + " con el identificador del negocio.");
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException e) {
            throw ErrorGateway.datosInvalidos("La cabecera " + CABECERA_NEGOCIO + " no es un identificador válido.");
        }
    }

    public static String correlationId(GraphQLContext contexto) {
        return contexto.get(CLAVE_CORRELACION);
    }

    /** Convierte un {@code ID} de GraphQL a UUID con un mensaje que nombra el argumento. */
    public static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor == null ? "" : valor.strip());
        } catch (IllegalArgumentException e) {
            throw ErrorGateway.datosInvalidos("El identificador de " + campo + " no es válido.");
        }
    }
}

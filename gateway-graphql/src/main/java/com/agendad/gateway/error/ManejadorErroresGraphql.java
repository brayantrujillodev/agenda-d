package com.agendad.gateway.error;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Traduce toda excepción de un resolver al formato estándar de GraphQL:
 * {@code message} en español, {@code extensions.codigo} y
 * {@code extensions.classification}. Nunca un stacktrace ni el mensaje
 * técnico de una excepción inesperada: eso se queda en el log.
 *
 * <p>El error queda en el campo que falló, no en toda la consulta: si
 * {@code metricasDelMes} falla, la agenda del día igual se muestra.
 */
@Component
public class ManejadorErroresGraphql extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGraphql.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ErrorGateway e) {
            return error(env, e.getTipo(), e.getCodigo(), e.getMessage());
        }
        if (ex instanceof ConstraintViolationException e) {
            String mensaje = e.getConstraintViolations().stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Revisa los datos enviados.");
            return error(env, ErrorType.BAD_REQUEST, "DATOS_INVALIDOS", mensaje);
        }
        log.error("Error inesperado resolviendo {}", env.getExecutionStepInfo().getPath(), ex);
        return error(env, ErrorType.INTERNAL_ERROR, "ERROR_INTERNO",
                "Ocurrió un error inesperado en el gateway. Intenta de nuevo en un momento.");
    }

    private static GraphQLError error(DataFetchingEnvironment env, ErrorType tipo, String codigo, String mensaje) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(tipo)
                .message(mensaje)
                .extensions(Map.of("codigo", codigo))
                .build();
    }
}

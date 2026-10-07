package com.agendad.gateway.config;

import com.agendad.gateway.client.ErrorDeAgenda;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Sin esto Spring GraphQL oculta el mensaje de la excepción ("INTERNAL_ERROR
 * for …") y el panel no puede decirle a la recepcionista por qué falló.
 */
@Component
public class ErroresGraphql extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ErrorDeAgenda) {
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.getMessage())
                    .build();
        }
        return null;
    }
}

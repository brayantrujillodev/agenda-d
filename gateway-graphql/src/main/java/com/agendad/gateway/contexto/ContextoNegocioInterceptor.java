package com.agendad.gateway.contexto;

import java.util.UUID;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Pasa las cabeceras HTTP al contexto de GraphQL, donde las leen los
 * resolvers. No valida aquí: un error en un interceptor tumbaría la
 * respuesta entera; en el resolver queda como un error GraphQL normal.
 */
@Component
public class ContextoNegocioInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String negocioId = request.getHeaders().getFirst(ContextoNegocio.CABECERA_NEGOCIO);
        String recibida = request.getHeaders().getFirst(ContextoNegocio.CABECERA_CORRELACION);
        String correlationId = recibida == null || recibida.isBlank() ? UUID.randomUUID().toString() : recibida;

        request.configureExecutionInput((input, builder) -> builder.graphQLContext(contexto -> {
            if (negocioId != null) {
                contexto.put(ContextoNegocio.CLAVE_NEGOCIO, negocioId);
            }
            contexto.put(ContextoNegocio.CLAVE_CORRELACION, correlationId);
        }).build());
        return chain.next(request);
    }
}

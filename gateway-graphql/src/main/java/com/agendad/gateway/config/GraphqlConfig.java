package com.agendad.gateway.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AbortExecutionException;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphqlConfig {

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
            .scalar(scalar("Instant", Instant.class, Instant::parse, value -> value.toString(), false))
            .scalar(scalar("Date", LocalDate.class, LocalDate::parse, value -> value.toString(), false))
            // Un precio puede llegar como 25000 o como "25000": los dos son claros.
            .scalar(scalar("Decimal", BigDecimal.class, BigDecimal::new, value -> value.toPlainString(), true));
    }

    /**
     * Corta las consultas demasiado anidadas antes de ejecutarlas, p. ej.
     * {@code negocio { profesionales { agenda { profesional { agenda ... } } } } },
     * que multiplicarían las llamadas a agenda-service. Con 15, la consulta de
     * introspección de GraphiQL pasa (verificado en docs/evidencias/P3-ENTREGA-1.md).
     */
    @Bean
    MaxQueryDepthInstrumentation profundidadMaxima(
            @Value("${agendad.graphql.profundidad-maxima}") int maxima) {
        return new MaxQueryDepthInstrumentation(maxima) {
            @Override
            protected AbortExecutionException mkAbortException(int profundidad, int maxProfundidad) {
                return new AbortExecutionException("La consulta es demasiado profunda: tiene " + profundidad
                        + " niveles y el máximo es " + maxProfundidad + ".");
            }
        };
    }

    /** Tope de campos por consulta (cada campo cuenta 1). */
    @Bean
    MaxQueryComplexityInstrumentation complejidadMaxima(
            @Value("${agendad.graphql.complejidad-maxima}") int maxima) {
        return new MaxQueryComplexityInstrumentation(maxima) {
            @Override
            protected AbortExecutionException mkAbortException(int complejidad, int maxComplejidad) {
                return new AbortExecutionException("La consulta pide demasiados campos: " + complejidad
                        + " y el máximo es " + maxComplejidad + ". Divídela en consultas más pequeñas.");
            }
        };
    }

    private <T> GraphQLScalarType scalar(String name, Class<T> type,
                                         Parser<T> parser, Serializer<T> serializer, boolean aceptaNumeros) {
        Coercing<T, String> coercing = new Coercing<>() {
            @Override
            public String serialize(Object input) {
                if (!type.isInstance(input)) {
                    throw new CoercingSerializeException("Valor inválido para " + name);
                }
                return serializer.serialize(type.cast(input));
            }

            @Override
            public T parseValue(Object input) {
                if (aceptaNumeros && input instanceof Number numero) {
                    return parsear(numero.toString());
                }
                if (!(input instanceof String value)) {
                    throw new CoercingParseValueException("Se esperaba texto para " + name);
                }
                return parsear(value);
            }

            @Override
            public T parseLiteral(Object input) {
                if (aceptaNumeros && (input instanceof IntValue || input instanceof FloatValue)) {
                    return parsear(literalNumerico(input));
                }
                if (!(input instanceof StringValue value)) {
                    throw new CoercingParseLiteralException("Se esperaba texto para " + name);
                }
                return parsear(value.getValue());
            }

            private T parsear(String value) {
                try {
                    return parser.parse(value);
                } catch (RuntimeException exception) {
                    throw new CoercingParseValueException("Valor inválido para " + name + ": " + value, exception);
                }
            }
        };
        return GraphQLScalarType.newScalar().name(name).coercing(coercing).build();
    }

    private static String literalNumerico(Object valor) {
        return valor instanceof IntValue entero ? entero.getValue().toString() : ((FloatValue) valor).getValue().toString();
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String value);
    }

    @FunctionalInterface
    private interface Serializer<T> {
        String serialize(T value);
    }
}

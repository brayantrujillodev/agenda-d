package com.agendad.gateway.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Configuration
public class GraphqlConfig {

    @Bean
    RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
            .scalar(scalar("Instant", Instant.class, Instant::parse, value -> value.toString()))
            .scalar(scalar("Date", LocalDate.class, LocalDate::parse, value -> value.toString()))
            .scalar(scalar("Decimal", BigDecimal.class, BigDecimal::new, value -> value.toPlainString()));
    }

    private <T> GraphQLScalarType scalar(String name, Class<T> type,
                                         Parser<T> parser, Serializer<T> serializer) {
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
                if (!(input instanceof String value)) {
                    throw new CoercingParseValueException("Se esperaba texto para " + name);
                }
                try {
                    return parser.parse(value);
                } catch (RuntimeException exception) {
                    throw new CoercingParseValueException("Valor inválido para " + name, exception);
                }
            }

            @Override
            public T parseLiteral(Object input) {
                if (!(input instanceof StringValue value)) {
                    throw new CoercingParseLiteralException("Se esperaba texto para " + name);
                }
                return parseValue(value.getValue());
            }
        };
        return GraphQLScalarType.newScalar().name(name).coercing(coercing).build();
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
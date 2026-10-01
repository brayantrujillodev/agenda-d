package com.agendad.agenda.comun;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un campo de texto solo acepta texto: {@code "clienteCelular": 3001234567}
 * (número) es un 400, no se convierte por debajo. La propiedad
 * {@code allow-coercion-of-scalars} no cubre número → texto en Jackson 2.17;
 * hay que decirlo con la configuración de coerción.
 */
@Configuration
public class JacksonEstricto {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer textoSoloDesdeTexto() {
        return builder -> builder.postConfigurer(mapper -> mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}

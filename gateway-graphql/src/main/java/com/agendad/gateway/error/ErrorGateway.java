package com.agendad.gateway.error;

import org.springframework.graphql.execution.ErrorType;

/**
 * Error que el gateway expone tal cual en {@code errors[]}: mensaje en
 * español y accionable, {@code codigo} en {@code extensions} y la
 * clasificación estándar de Spring for GraphQL. Lo traduce
 * {@link ManejadorErroresGraphql}.
 *
 * <p>Los errores de agenda-service y analitica-service llegan aquí con su
 * mismo {@code codigo} y {@code mensaje} (ver
 * {@code client/TraductorErroresHttp}): el gateway no los reescribe.
 */
public class ErrorGateway extends RuntimeException {

    private final String codigo;
    private final ErrorType tipo;

    public ErrorGateway(String codigo, String mensaje, ErrorType tipo) {
        super(mensaje);
        this.codigo = codigo;
        this.tipo = tipo;
    }

    public static ErrorGateway datosInvalidos(String mensaje) {
        return new ErrorGateway("DATOS_INVALIDOS", mensaje, ErrorType.BAD_REQUEST);
    }

    public String getCodigo() {
        return codigo;
    }

    public ErrorType getTipo() {
        return tipo;
    }
}

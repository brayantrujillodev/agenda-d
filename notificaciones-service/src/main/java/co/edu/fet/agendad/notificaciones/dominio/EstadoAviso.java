package co.edu.fet.agendad.notificaciones.dominio;

/**
 * Valores de {@code notificaciones.programacion.estado}. Deben coincidir
 * exactamente con el CHECK {@code prog_estado_valido} del esquema.
 */
public enum EstadoAviso {
    PENDIENTE,
    ENVIADO,
    FALLIDO,
    CANCELADO
}

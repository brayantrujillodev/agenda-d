package co.edu.fet.agendad.notificaciones.dominio;

/**
 * Valores de {@code notificaciones.programacion.tipo}.
 *
 * <p>{@code RECORDATORIO_24H} está aquí porque ya es parte del vocabulario
 * fijado en el esquema (db/V1__esquema_inicial.sql), pero nadie lo produce
 * todavía: la programación y el disparo del recordatorio son la Fase 3
 * (issue #18). No adelantar esa lógica aquí.
 */
public enum TipoAviso {
    CONFIRMACION,
    RECORDATORIO_24H,
    CANCELACION
}

package co.edu.fet.agendad.notificaciones.evento;

/**
 * Sub-objeto {@code cliente} tal como aparece en ambos eventos del
 * contrato (docs/eventos/CONTRATO-EVENTOS.md).
 */
public record ClienteInfo(String nombre, String celular) {
}

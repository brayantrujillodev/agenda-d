package co.edu.fet.agendad.notificaciones.canal;

import co.edu.fet.agendad.notificaciones.dominio.Programacion;

/**
 * Abstrae cómo se hace llegar un aviso al cliente. Hoy solo existe
 * {@link RegistroCanal} (guarda el mensaje en base de datos, sin costo).
 *
 * <p>WhatsApp, SMS y correo (ver {@code agenda.negocio.canal_primario} en
 * el esquema) son implementaciones futuras que no se adelantan aquí —
 * docs/TAREAS.md las deja fuera de la Fase 2.
 */
public interface CanalNotificacion {

    /** Nombre del canal, para dejar registro de por dónde "salió" el aviso. */
    String nombre();

    /**
     * Procesa el envío del aviso. La implementación decide cómo dejar
     * constancia: actualiza estado, canal, intentos y enviadoEn, y lo
     * persiste.
     */
    void enviar(Programacion aviso);
}

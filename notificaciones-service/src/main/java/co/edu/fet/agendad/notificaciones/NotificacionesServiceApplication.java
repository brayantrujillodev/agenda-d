package co.edu.fet.agendad.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} activa {@link co.edu.fet.agendad.notificaciones.consumidor.RecordatorioScheduler}
 * (docs/TAREAS.md #18).
 */
@SpringBootApplication
@EnableScheduling
public class NotificacionesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificacionesServiceApplication.class, args);
    }
}

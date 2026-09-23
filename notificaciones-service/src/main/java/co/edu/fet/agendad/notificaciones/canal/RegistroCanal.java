package co.edu.fet.agendad.notificaciones.canal;

import co.edu.fet.agendad.notificaciones.dominio.EstadoAviso;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.ProgramacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Adaptador REGISTRO (docker-compose.yml, {@code AGENDAD_CANAL_POR_DEFECTO}):
 * en vez de enviar el mensaje por WhatsApp/SMS/correo, lo guarda en
 * {@code notificaciones.programacion} como si ya hubiera salido. Sin costo,
 * sin proveedor externo — es el único canal de la Fase 2.
 */
@Component
public class RegistroCanal implements CanalNotificacion {

    private static final Logger log = LoggerFactory.getLogger(RegistroCanal.class);
    private static final String NOMBRE = "REGISTRO";

    private final ProgramacionRepository programacionRepository;

    public RegistroCanal(ProgramacionRepository programacionRepository,
                          @Value("${agendad.canal-por-defecto:REGISTRO}") String canalConfigurado) {
        this.programacionRepository = programacionRepository;
        if (!NOMBRE.equals(canalConfigurado)) {
            log.warn("agendad.canal-por-defecto={} pero solo REGISTRO está implementado en esta fase; "
                    + "se usará REGISTRO de todas formas.", canalConfigurado);
        }
    }

    @Override
    public String nombre() {
        return NOMBRE;
    }

    @Override
    public void enviar(Programacion aviso) {
        aviso.setCanal(NOMBRE);
        aviso.setEstado(EstadoAviso.ENVIADO);
        aviso.setEnviadoEn(Instant.now());
        aviso.setIntentos(aviso.getIntentos() + 1);
        programacionRepository.save(aviso);
        log.info("Aviso {} registrado para la cita {} (canal REGISTRO)", aviso.getTipo(), aviso.getCitaId());
    }
}

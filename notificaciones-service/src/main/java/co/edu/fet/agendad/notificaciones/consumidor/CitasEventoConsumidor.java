package co.edu.fet.agendad.notificaciones.consumidor;

import co.edu.fet.agendad.notificaciones.canal.CanalNotificacion;
import co.edu.fet.agendad.notificaciones.dominio.EventoProcesado;
import co.edu.fet.agendad.notificaciones.dominio.EventoProcesadoRepository;
import co.edu.fet.agendad.notificaciones.dominio.Programacion;
import co.edu.fet.agendad.notificaciones.dominio.TipoAviso;
import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Consume {@code citas.reservadas} y {@code citas.canceladas}
 * (docs/eventos/CONTRATO-EVENTOS.md, issue #10).
 *
 * <p>Antes de procesar cada evento se verifica
 * {@code notificaciones.evento_procesado}: Kafka entrega "al menos una
 * vez", así que un mismo {@code eventoId} puede reentregarse y no debe
 * generar un segundo aviso.
 *
 * <p>Fase 2 únicamente: confirmación y cancelación. El recordatorio de
 * 24 horas es la Fase 3 (issue #18) y no se toca en esta clase.
 */
@Component
public class CitasEventoConsumidor {

    private static final Logger log = LoggerFactory.getLogger(CitasEventoConsumidor.class);
    private static final DateTimeFormatter FORMATO_FECHA_LOCAL = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    // El contrato de citas.canceladas no trae zonaHoraria (a diferencia de
    // citas.reservadas); se usa el valor por defecto de negocio.zona_horaria.
    private static final String ZONA_HORARIA_POR_DEFECTO = "America/Bogota";

    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final CanalNotificacion canalNotificacion;

    public CitasEventoConsumidor(EventoProcesadoRepository eventoProcesadoRepository,
                                  CanalNotificacion canalNotificacion) {
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.canalNotificacion = canalNotificacion;
    }

    @KafkaListener(topics = "citas.reservadas", groupId = "notificaciones-service",
            containerFactory = "citaReservadaListenerFactory")
    @Transactional
    public void alReservarCita(CitaReservadaEvento evento) {
        if (yaProcesado(evento.eventoId())) {
            return;
        }

        String horaLocal = formatearEnZonaLocal(evento.inicio(), evento.zonaHoraria());
        String cuerpo = "Hola %s, tu cita de %s con %s quedó confirmada para el %s. Gestiónala en tu enlace: %s"
                .formatted(evento.cliente().nombre(), evento.servicioNombre(), evento.profesionalNombre(),
                        horaLocal, evento.tokenGestion());

        Programacion aviso = new Programacion(
                UUID.randomUUID(), evento.negocioId(), evento.citaId(), TipoAviso.CONFIRMACION,
                Instant.now(), evento.cliente().celular(), cuerpo);

        procesarYMarcar(aviso, evento.eventoId());
    }

    @KafkaListener(topics = "citas.canceladas", groupId = "notificaciones-service",
            containerFactory = "citaCanceladaListenerFactory")
    @Transactional
    public void alCancelarCita(CitaCanceladaEvento evento) {
        if (yaProcesado(evento.eventoId())) {
            return;
        }

        String horaLocal = formatearEnZonaLocal(evento.inicio(), ZONA_HORARIA_POR_DEFECTO);
        String quienCancelo = "CLIENTE".equals(evento.canceladaPor()) ? "el cliente" : "el negocio";
        String cuerpo = "Hola %s, tu cita del %s fue cancelada por %s. Si fue un error, agenda de nuevo desde el enlace del negocio."
                .formatted(evento.cliente().nombre(), horaLocal, quienCancelo);

        Programacion aviso = new Programacion(
                UUID.randomUUID(), evento.negocioId(), evento.citaId(), TipoAviso.CANCELACION,
                Instant.now(), evento.cliente().celular(), cuerpo);

        procesarYMarcar(aviso, evento.eventoId());
    }

    private boolean yaProcesado(UUID eventoId) {
        boolean existe = eventoProcesadoRepository.existsById(eventoId);
        if (existe) {
            log.debug("Evento {} ya procesado, se ignora (reentrega de Kafka)", eventoId);
        }
        return existe;
    }

    private void procesarYMarcar(Programacion aviso, UUID eventoId) {
        canalNotificacion.enviar(aviso);
        eventoProcesadoRepository.save(new EventoProcesado(eventoId, Instant.now()));
    }

    private String formatearEnZonaLocal(Instant instante, String zonaHoraria) {
        ZoneId zona = ZoneId.of(zonaHoraria);
        return ZonedDateTime.ofInstant(instante, zona).format(FORMATO_FECHA_LOCAL);
    }
}

package com.agendad.analitica.consumidor;

import com.agendad.analitica.dominio.EventoProcesado;
import com.agendad.analitica.evento.EventoCitaCancelada;
import com.agendad.analitica.evento.EventoCitaEstado;
import com.agendad.analitica.evento.EventoCitaReservada;
import com.agendad.analitica.repositorio.EventoProcesadoRepository;
import com.agendad.analitica.repositorio.MetricaDiariaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Consume citas.reservadas, citas.canceladas y citas.estado
 * (docs/eventos/CONTRATO-EVENTOS.md, docs/TAREAS.md #19) y actualiza
 * analitica.metrica_diaria.
 *
 * <p>Antes de procesar cada evento se verifica
 * {@code analitica.evento_procesado}: Kafka entrega "al menos una vez",
 * así que un mismo {@code eventoId} puede reentregarse y no debe contarse
 * dos veces — mismo patrón que notificaciones-service.
 */
@Component
public class MetricasConsumidor {

    private static final Logger log = LoggerFactory.getLogger(MetricasConsumidor.class);

    // citas.canceladas y citas.estado no traen zonaHoraria (a diferencia de
    // citas.reservadas): se usa el valor por defecto de negocio.zona_horaria,
    // mismo enfoque ya aceptado en notificaciones-service.
    private final String zonaHorariaPorDefecto;

    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final MetricaDiariaRepository metricaDiariaRepository;

    public MetricasConsumidor(EventoProcesadoRepository eventoProcesadoRepository,
                               MetricaDiariaRepository metricaDiariaRepository,
                               @Value("${agendad.metricas.zona-horaria-por-defecto}") String zonaHorariaPorDefecto) {
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.metricaDiariaRepository = metricaDiariaRepository;
        this.zonaHorariaPorDefecto = zonaHorariaPorDefecto;
    }

    @KafkaListener(topics = "citas.reservadas", groupId = "analitica-service",
            containerFactory = "citaReservadaListenerFactory")
    @Transactional
    public void alReservarCita(EventoCitaReservada evento) {
        if (yaProcesado(evento.eventoId())) {
            return;
        }
        LocalDate fecha = fechaLocal(evento.inicio(), evento.zonaHoraria());
        long minutos = minutosEntre(evento.inicio(), evento.fin());
        metricaDiariaRepository.registrarReserva(
                evento.negocioId(), fecha, evento.profesionalId(), evento.servicioId(), minutos);
        marcarProcesado(evento.eventoId());
    }

    @KafkaListener(topics = "citas.canceladas", groupId = "analitica-service",
            containerFactory = "citaCanceladaListenerFactory")
    @Transactional
    public void alCancelarCita(EventoCitaCancelada evento) {
        if (yaProcesado(evento.eventoId())) {
            return;
        }
        LocalDate fecha = fechaLocal(evento.inicio(), zonaHorariaPorDefecto);
        long minutos = minutosEntre(evento.inicio(), evento.fin());
        metricaDiariaRepository.registrarCancelacion(
                evento.negocioId(), fecha, evento.profesionalId(), evento.servicioId(), minutos);
        marcarProcesado(evento.eventoId());
    }

    @KafkaListener(topics = "citas.estado", groupId = "analitica-service",
            containerFactory = "citaEstadoListenerFactory")
    @Transactional
    public void alRegistrarEstado(EventoCitaEstado evento) {
        if (yaProcesado(evento.eventoId())) {
            return;
        }
        LocalDate fecha = fechaLocal(evento.inicio(), zonaHorariaPorDefecto);
        boolean atendida = EventoCitaEstado.ATENDIDA.equals(evento.estadoNuevo());
        metricaDiariaRepository.registrarAsistencia(
                evento.negocioId(), fecha, evento.profesionalId(), evento.servicioId(), atendida);
        marcarProcesado(evento.eventoId());
    }

    private boolean yaProcesado(UUID eventoId) {
        boolean existe = eventoProcesadoRepository.existsById(eventoId);
        if (existe) {
            log.debug("Evento {} ya procesado, se ignora (reentrega de Kafka)", eventoId);
        }
        return existe;
    }

    private void marcarProcesado(UUID eventoId) {
        eventoProcesadoRepository.save(new EventoProcesado(eventoId, Instant.now()));
    }

    private LocalDate fechaLocal(Instant instante, String zonaHoraria) {
        return instante.atZone(ZoneId.of(zonaHoraria)).toLocalDate();
    }

    private long minutosEntre(Instant inicio, Instant fin) {
        return Duration.between(inicio, fin).toMinutes();
    }
}

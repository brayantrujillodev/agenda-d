package com.agendad.analitica.config;

import com.agendad.analitica.evento.EventoCitaCancelada;
import com.agendad.analitica.evento.EventoCitaEstado;
import com.agendad.analitica.evento.EventoCitaReservada;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * citas.reservadas, citas.canceladas y citas.estado traen tipos de evento
 * distintos, y el productor publica JSON plano sin cabeceras de tipo de
 * Spring (docs/eventos/CONTRATO-EVENTOS.md: "Es JSON con campo version",
 * nada de Avro/Schema Registry). Por eso cada tópico tiene su propia
 * fábrica con el tipo de destino fijado explícitamente en el
 * JsonDeserializer — mismo patrón que notificaciones-service.
 *
 * <p>docs/TAREAS.md #16: si un listener falla, se reintenta tres veces con
 * espera creciente (1 s, 4 s, 16 s) y, agotados los intentos, el mensaje se
 * publica en {@code citas.dlq} (misma cola compartida que usa
 * notificaciones-service, que es quien la consume y la expone por REST —
 * un solo lugar centraliza "lo que cayó", no uno por servicio).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String PAQUETE_EVENTOS = "com.agendad.analitica.evento";
    private static final String TOPICO_DLQ = "citas.dlq";

    @Bean
    public ConsumerFactory<String, EventoCitaReservada> citaReservadaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaReservada.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaReservada> citaReservadaListenerFactory(
            ConsumerFactory<String, EventoCitaReservada> citaReservadaConsumerFactory,
            DefaultErrorHandler manejadorDeErrores) {
        return fabricaListener(citaReservadaConsumerFactory, manejadorDeErrores);
    }

    @Bean
    public ConsumerFactory<String, EventoCitaCancelada> citaCanceladaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaCancelada.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaCancelada> citaCanceladaListenerFactory(
            ConsumerFactory<String, EventoCitaCancelada> citaCanceladaConsumerFactory,
            DefaultErrorHandler manejadorDeErrores) {
        return fabricaListener(citaCanceladaConsumerFactory, manejadorDeErrores);
    }

    @Bean
    public ConsumerFactory<String, EventoCitaEstado> citaEstadoConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaEstado.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaEstado> citaEstadoListenerFactory(
            ConsumerFactory<String, EventoCitaEstado> citaEstadoConsumerFactory,
            DefaultErrorHandler manejadorDeErrores) {
        return fabricaListener(citaEstadoConsumerFactory, manejadorDeErrores);
    }

    @Bean
    public DeadLetterPublishingRecoverer dlqRecoverer(KafkaTemplate<String, Object> dlqKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                (record, ex) -> new TopicPartition(TOPICO_DLQ, -1));
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> propiedades = kafkaProperties.buildProducerProperties(null);
        // Un mensaje que ni siquiera era JSON llega aquí como los byte[]
        // originales (ErrorHandlingDeserializer los conserva): se publican
        // tal cual. Todo lo demás se reserializa a JSON.
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());
        porTipo.put(Object.class, new JsonSerializer<>());
        Serializer<Object> valor = new DelegatingByTypeSerializer(porTipo, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(propiedades, new StringSerializer(), valor));
    }

    /** Tres reintentos con espera creciente (1 s, 4 s, 16 s), luego {@link #dlqRecoverer}. */
    @Bean
    public DefaultErrorHandler manejadorDeErrores(DeadLetterPublishingRecoverer dlqRecoverer) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(4.0);
        backOff.setMaxInterval(16_000L);
        return new DefaultErrorHandler(dlqRecoverer, backOff);
    }

    private <T> ConsumerFactory<String, T> fabricaPara(KafkaProperties kafkaProperties, Class<T> tipoEvento) {
        JsonDeserializer<T> json = new JsonDeserializer<>(tipoEvento, false);
        json.addTrustedPackages(PAQUETE_EVENTOS);
        // Sin este envoltorio, un mensaje que no es JSON válido falla dentro
        // de poll(), antes de llegar al listener: DefaultErrorHandler no
        // puede manejarlo y el consumidor queda en bucle sobre el mismo
        // offset, con la partición bloqueada. Envuelto, el fallo llega al
        // manejador como DeserializationException (no reintentable) y el
        // mensaje va directo a citas.dlq.
        ErrorHandlingDeserializer<T> deserializer = new ErrorHandlingDeserializer<>(json);
        Map<String, Object> propiedades = kafkaProperties.buildConsumerProperties(null);
        return new DefaultKafkaConsumerFactory<>(propiedades, new StringDeserializer(), deserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> fabricaListener(
            ConsumerFactory<String, T> consumerFactory, DefaultErrorHandler manejadorDeErrores) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(manejadorDeErrores);
        return factory;
    }
}

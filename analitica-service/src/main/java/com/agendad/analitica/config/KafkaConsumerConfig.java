package com.agendad.analitica.config;

import com.agendad.analitica.evento.EventoCitaCancelada;
import com.agendad.analitica.evento.EventoCitaEstado;
import com.agendad.analitica.evento.EventoCitaReservada;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

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
    public DeadLetterPublishingRecoverer dlqRecoverer(KafkaTemplate<Object, Object> dlqKafkaTemplate) {
        return new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                (record, ex) -> new TopicPartition(TOPICO_DLQ, -1));
    }

    @Bean
    public KafkaTemplate<Object, Object> dlqKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> propiedades = kafkaProperties.buildProducerProperties(null);
        propiedades.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        propiedades.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(propiedades));
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
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(tipoEvento, false);
        deserializer.addTrustedPackages(PAQUETE_EVENTOS);
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

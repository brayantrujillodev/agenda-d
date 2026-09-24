package com.agendad.analitica.config;

import com.agendad.analitica.evento.EventoCitaCancelada;
import com.agendad.analitica.evento.EventoCitaEstado;
import com.agendad.analitica.evento.EventoCitaReservada;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * citas.reservadas, citas.canceladas y citas.estado traen tipos de evento
 * distintos, y el productor publica JSON plano sin cabeceras de tipo de
 * Spring (docs/eventos/CONTRATO-EVENTOS.md: "Es JSON con campo version",
 * nada de Avro/Schema Registry). Por eso cada tópico tiene su propia
 * fábrica con el tipo de destino fijado explícitamente en el
 * JsonDeserializer — mismo patrón que notificaciones-service.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String PAQUETE_EVENTOS = "com.agendad.analitica.evento";

    @Bean
    public ConsumerFactory<String, EventoCitaReservada> citaReservadaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaReservada.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaReservada> citaReservadaListenerFactory(
            ConsumerFactory<String, EventoCitaReservada> citaReservadaConsumerFactory) {
        return fabricaListener(citaReservadaConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, EventoCitaCancelada> citaCanceladaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaCancelada.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaCancelada> citaCanceladaListenerFactory(
            ConsumerFactory<String, EventoCitaCancelada> citaCanceladaConsumerFactory) {
        return fabricaListener(citaCanceladaConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, EventoCitaEstado> citaEstadoConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, EventoCitaEstado.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventoCitaEstado> citaEstadoListenerFactory(
            ConsumerFactory<String, EventoCitaEstado> citaEstadoConsumerFactory) {
        return fabricaListener(citaEstadoConsumerFactory);
    }

    private <T> ConsumerFactory<String, T> fabricaPara(KafkaProperties kafkaProperties, Class<T> tipoEvento) {
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(tipoEvento, false);
        deserializer.addTrustedPackages(PAQUETE_EVENTOS);
        Map<String, Object> propiedades = kafkaProperties.buildConsumerProperties(null);
        return new DefaultKafkaConsumerFactory<>(propiedades, new StringDeserializer(), deserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> fabricaListener(ConsumerFactory<String, T> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}

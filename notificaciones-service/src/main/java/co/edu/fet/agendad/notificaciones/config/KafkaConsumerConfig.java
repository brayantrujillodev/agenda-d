package co.edu.fet.agendad.notificaciones.config;

import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
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
 * citas.reservadas y citas.canceladas traen tipos de evento distintos, y el
 * productor publica JSON plano sin cabeceras de tipo de Spring (ver
 * docs/eventos/CONTRATO-EVENTOS.md: "Es JSON con campo version", nada de
 * Avro/Schema Registry ni tipos de Spring). Por eso cada tópico tiene su
 * propia fábrica con el tipo de destino fijado explícitamente en el
 * JsonDeserializer, en vez de un único {@code spring.json.value.default.type}
 * global.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String PAQUETE_EVENTOS = "co.edu.fet.agendad.notificaciones.evento";

    @Bean
    public ConsumerFactory<String, CitaReservadaEvento> citaReservadaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, CitaReservadaEvento.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CitaReservadaEvento> citaReservadaListenerFactory(
            ConsumerFactory<String, CitaReservadaEvento> citaReservadaConsumerFactory) {
        return fabricaListener(citaReservadaConsumerFactory);
    }

    @Bean
    public ConsumerFactory<String, CitaCanceladaEvento> citaCanceladaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, CitaCanceladaEvento.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CitaCanceladaEvento> citaCanceladaListenerFactory(
            ConsumerFactory<String, CitaCanceladaEvento> citaCanceladaConsumerFactory) {
        return fabricaListener(citaCanceladaConsumerFactory);
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

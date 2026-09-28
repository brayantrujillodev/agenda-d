package co.edu.fet.agendad.notificaciones.config;

import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * citas.reservadas y citas.canceladas traen tipos de evento distintos, y el
 * productor publica JSON plano sin cabeceras de tipo de Spring (ver
 * docs/eventos/CONTRATO-EVENTOS.md: "Es JSON con campo version", nada de
 * Avro/Schema Registry ni tipos de Spring). Por eso cada tópico tiene su
 * propia fábrica con el tipo de destino fijado explícitamente en el
 * JsonDeserializer, en vez de un único {@code spring.json.value.default.type}
 * global.
 *
 * <p>docs/TAREAS.md #16: si el listener de cualquiera de las dos fábricas
 * lanza una excepción, se reintenta tres veces con espera creciente
 * (1 s, 4 s, 16 s — docs/eventos/CONTRATO-EVENTOS.md) y, agotados los
 * intentos, el mensaje se publica en {@code citas.dlq} en vez de perderse
 * o de bloquear la partición. El manejador de errores es común a ambas
 * fábricas.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final String PAQUETE_EVENTOS = "co.edu.fet.agendad.notificaciones.evento";
    private static final String TOPICO_DLQ = "citas.dlq";

    @Bean
    public ConsumerFactory<String, CitaReservadaEvento> citaReservadaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, CitaReservadaEvento.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CitaReservadaEvento> citaReservadaListenerFactory(
            ConsumerFactory<String, CitaReservadaEvento> citaReservadaConsumerFactory,
            DefaultErrorHandler manejadorDeErrores) {
        return fabricaListener(citaReservadaConsumerFactory, manejadorDeErrores);
    }

    @Bean
    public ConsumerFactory<String, CitaCanceladaEvento> citaCanceladaConsumerFactory(KafkaProperties kafkaProperties) {
        return fabricaPara(kafkaProperties, CitaCanceladaEvento.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CitaCanceladaEvento> citaCanceladaListenerFactory(
            ConsumerFactory<String, CitaCanceladaEvento> citaCanceladaConsumerFactory,
            DefaultErrorHandler manejadorDeErrores) {
        return fabricaListener(citaCanceladaConsumerFactory, manejadorDeErrores);
    }

    /**
     * citas.dlq recibe eventos de distintos tipos originales (reservada,
     * cancelada, estado) ya reserializados a JSON plano por
     * {@link #dlqRecoverer}: no hace falta el tipado por evento que usan
     * las otras fábricas, basta con {@link String} — lo que importa aquí
     * es guardar el contenido, no interpretarlo.
     */
    @Bean
    public ConsumerFactory<String, String> citasDlqConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> propiedades = kafkaProperties.buildConsumerProperties(null);
        return new DefaultKafkaConsumerFactory<>(propiedades, new StringDeserializer(), new StringDeserializer());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> citasDlqListenerFactory(
            ConsumerFactory<String, String> citasDlqConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(citasDlqConsumerFactory);
        // Sin manejadorDeErrores aquí a propósito: si guardar un mensaje ya
        // caído en citas.dlq también falla, reintentar con el mismo
        // DefaultErrorHandler lo mandaría... a citas.dlq otra vez. Un fallo
        // real aquí (p. ej. Postgres caído) debe verse en los logs, no
        // desaparecer en un bucle silencioso.
        return factory;
    }

    /**
     * Publica en {@code citas.dlq} sin importar de qué tópico venía el
     * mensaje fallido (el contrato define una sola cola de fallidos para
     * los tres tópicos de citas). El valor se reserializa a JSON con
     * {@link JsonSerializer} — no son los bytes originales exactos, pero sí
     * el mismo contenido, que es lo que importa para poder reprocesarlo o
     * inspeccionarlo.
     */
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

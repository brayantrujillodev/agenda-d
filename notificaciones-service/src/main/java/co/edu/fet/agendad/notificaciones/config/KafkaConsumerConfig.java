package co.edu.fet.agendad.notificaciones.config;

import co.edu.fet.agendad.notificaciones.evento.CitaCanceladaEvento;
import co.edu.fet.agendad.notificaciones.evento.CitaReservadaEvento;
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
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
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

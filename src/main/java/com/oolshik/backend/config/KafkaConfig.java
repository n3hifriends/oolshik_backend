package com.oolshik.backend.config;

import com.oolshik.backend.transcription.SttJobMessage;
import com.oolshik.backend.transcription.SttResultMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties({KafkaTopicProperties.class, NotificationProperties.class})
public class KafkaConfig {

    @Bean
    public ConsumerFactory<String, SttResultMessage> sttResultConsumerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SttResultMessage.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.oolshik.backend.transcription");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(SttResultMessage.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SttResultMessage> sttResultKafkaListenerContainerFactory(
            ConsumerFactory<String, SttResultMessage> sttResultConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, SttResultMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sttResultConsumerFactory);
        return factory;
    }

    @Bean(name = "notificationProducerFactory")
    public ProducerFactory<String, String> notificationProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean(name = "notificationKafkaTemplate")
    public KafkaTemplate<String, String> notificationKafkaTemplate(
            ProducerFactory<String, String> notificationProducerFactory) {
        return new KafkaTemplate<>(notificationProducerFactory);
    }

    @Bean(name = "sttProducerFactory")
    public ProducerFactory<String, SttJobMessage> sttProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties());
        config.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean(name = "sttKafkaTemplate")
    public KafkaTemplate<String, SttJobMessage> sttKafkaTemplate(
            ProducerFactory<String, SttJobMessage> sttProducerFactory) {
        return new KafkaTemplate<>(sttProducerFactory);
    }
}

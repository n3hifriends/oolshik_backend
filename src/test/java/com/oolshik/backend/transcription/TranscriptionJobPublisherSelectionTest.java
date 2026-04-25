package com.oolshik.backend.transcription;

import com.oolshik.backend.config.KafkaTopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TranscriptionJobPublisherSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void selectsNoopPublisherWhenKafkaDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TranscriptionJobPublisher.class);
            assertThat(context.getBean(TranscriptionJobPublisher.class))
                    .isInstanceOf(NoopTranscriptionJobPublisher.class);
        });
    }

    @Test
    void selectsKafkaPublisherWhenKafkaEnabled() {
        contextRunner
                .withPropertyValues("app.messaging.kafka.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TranscriptionJobPublisher.class);
                    assertThat(context.getBean(TranscriptionJobPublisher.class))
                            .isInstanceOf(KafkaTranscriptionJobPublisher.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({NoopTranscriptionJobPublisher.class, KafkaTranscriptionJobPublisher.class})
    static class TestConfig {
        @Bean
        @ConditionalOnProperty(name = "app.messaging.kafka.enabled", havingValue = "true")
        KafkaTemplate<String, SttJobMessage> sttKafkaTemplate() {
            return mock(KafkaTemplate.class);
        }

        @Bean
        @ConditionalOnProperty(name = "app.messaging.kafka.enabled", havingValue = "true")
        KafkaTopicProperties kafkaTopicProperties() {
            KafkaTopicProperties properties = new KafkaTopicProperties();
            properties.setSttJobs("stt.jobs");
            return properties;
        }
    }
}

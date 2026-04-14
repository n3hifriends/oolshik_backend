package com.oolshik.backend.service;

import com.oolshik.backend.config.OtpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.Assertions.assertThat;

class OtpProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void selectsDevProviderByDefault() {
        contextRunner
                .withPropertyValues("app.otp.provider=dev")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtpProvider.class);
                    assertThat(context.getBean(OtpProvider.class)).isInstanceOf(DevOtpProvider.class);
                });
    }

    @Test
    void selectsMsg91ProviderWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "app.otp.provider=msg91",
                        "app.otp.msg91.api-key=test-key",
                        "app.otp.msg91.template-id=test-template",
                        "app.otp.msg91.base-url=http://localhost"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(OtpProvider.class);
                    assertThat(context.getBean(OtpProvider.class)).isInstanceOf(Msg91OtpProvider.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OtpProperties.class)
    static class TestConfig {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }

        @Bean
        @ConditionalOnProperty(name = "app.otp.provider", havingValue = "dev", matchIfMissing = true)
        OtpProvider devOtpProvider() {
            return new DevOtpProvider();
        }

        @Bean
        @ConditionalOnProperty(name = "app.otp.provider", havingValue = "msg91")
        OtpProvider msg91OtpProvider(RestTemplateBuilder restTemplateBuilder, OtpProperties otpProperties) {
            return new Msg91OtpProvider(restTemplateBuilder, otpProperties);
        }
    }
}

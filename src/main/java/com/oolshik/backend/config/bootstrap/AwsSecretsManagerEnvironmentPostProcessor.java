package com.oolshik.backend.config.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.Map;

public class AwsSecretsManagerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "awsSecretsManager";

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty("app.secrets.aws.enabled", Boolean.class, false);
        if (!enabled) {
            return;
        }

        String secretName = environment.getProperty("app.secrets.aws.secretName");
        boolean failFast = environment.getProperty("app.secrets.aws.failFast", Boolean.class, true);

        if (!StringUtils.hasText(secretName)) {
            handleFailure(failFast, "AWS Secrets Manager is enabled but app.secrets.aws.secretName is empty", null);
            return;
        }

        try (SecretsManagerClient client = buildClient(environment)) {
            GetSecretValueResponse response = client.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build());

            Map<String, Object> properties = BootstrapPropertySupport.parseSecretPayload(extractPayload(response));
            BootstrapPropertySupport.addBeforeSystemEnvironment(environment, PROPERTY_SOURCE_NAME, properties);
            log.info("Loaded configuration from AWS Secrets Manager secret {}", secretName);
        } catch (Exception e) {
            handleFailure(failFast, "Failed to load configuration from AWS Secrets Manager secret " + secretName, e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private SecretsManagerClient buildClient(ConfigurableEnvironment environment) {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create());

        String region = environment.getProperty("app.secrets.aws.region");
        if (StringUtils.hasText(region)) {
            builder.region(Region.of(region));
        }

        return builder.build();
    }

    private String extractPayload(GetSecretValueResponse response) {
        if (StringUtils.hasText(response.secretString())) {
            return response.secretString();
        }

        SdkBytes secretBinary = response.secretBinary();
        if (secretBinary != null) {
            return secretBinary.asUtf8String();
        }

        throw new IllegalStateException("AWS Secrets Manager response did not contain a secret payload");
    }

    private void handleFailure(boolean failFast, String message, Exception cause) {
        if (failFast) {
            throw new IllegalStateException(message, cause);
        }

        if (cause == null) {
            log.warn(message);
            return;
        }

        log.warn(message + ". Continuing without AWS secret overrides", cause);
    }
}

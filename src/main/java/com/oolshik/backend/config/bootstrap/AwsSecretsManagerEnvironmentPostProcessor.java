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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AwsSecretsManagerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "awsSecretsManager";

    private static final Logger log = LoggerFactory.getLogger(AwsSecretsManagerEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty("app.secrets.aws.enabled", Boolean.class, false);
        if (!enabled) {
            return;
        }

        boolean failFast = environment.getProperty("app.secrets.aws.failFast", Boolean.class, true);
        List<String> secretNames = resolveSecretNames(environment);

        if (secretNames.isEmpty()) {
            handleFailure(failFast,
                    "AWS Secrets Manager is enabled but no secret names are configured. Set app.secrets.aws.secretName, app.secrets.aws.dbSecretName, or app.secrets.aws.appSecretName",
                    null);
            return;
        }

        try (SecretsManagerClient client = buildClient(environment)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> loadedSecrets = new ArrayList<>();
            for (String secretName : secretNames) {
                try {
                    GetSecretValueResponse response = client.getSecretValue(GetSecretValueRequest.builder()
                            .secretId(secretName)
                            .build());
                    properties.putAll(BootstrapPropertySupport.parseSecretPayload(extractPayload(response)));
                    loadedSecrets.add(secretName);
                } catch (Exception e) {
                    handleFailure(failFast, "Failed to load configuration from AWS Secrets Manager secret " + secretName, e);
                }
            }

            if (properties.isEmpty()) {
                return;
            }

            BootstrapPropertySupport.addBeforeSystemEnvironment(environment, PROPERTY_SOURCE_NAME, properties);
            log.info("Loaded configuration from AWS Secrets Manager secrets {}", loadedSecrets);
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

    static List<String> resolveSecretNames(ConfigurableEnvironment environment) {
        Set<String> names = new LinkedHashSet<>();
        addDelimitedNames(names, getConfiguredProperty(environment, "app.secrets.aws.secretName"));
        addName(names, getConfiguredProperty(environment, "app.secrets.aws.dbSecretName"));
        addName(names, getConfiguredProperty(environment, "app.secrets.aws.appSecretName"));
        return List.copyOf(names);
    }

    private static void addDelimitedNames(Set<String> names, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        for (String token : raw.split(",")) {
            addName(names, token);
        }
    }

    private static void addName(Set<String> names, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        names.add(raw.trim());
    }

    private static String getConfiguredProperty(ConfigurableEnvironment environment, String key) {
        String value = environment.getProperty(key);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return environment.getProperty(toEnvKey(key));
    }

    private static String toEnvKey(String key) {
        return key.toUpperCase()
                .replace('.', '_')
                .replace('-', '_');
    }
}

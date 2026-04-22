package com.oolshik.backend.config.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapPropertySupportTest {

    @Test
    void parsesDotenvAndAddsSpringAliases() throws IOException {
        Path dotenv = Files.createTempFile("oolshik", ".env");
        Files.writeString(dotenv, """
                SPRING_PROFILES_ACTIVE=dev
                JWT_SECRET="quoted-secret"
                export DB_HOST=localhost
                APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS=a,b
                """);

        Map<String, Object> properties = BootstrapPropertySupport.parseDotenv(dotenv);

        assertThat(properties)
                .containsEntry("SPRING_PROFILES_ACTIVE", "dev")
                .containsEntry("spring.profiles.active", "dev")
                .containsEntry("JWT_SECRET", "quoted-secret")
                .containsEntry("jwt.secret", "quoted-secret")
                .containsEntry("DB_HOST", "localhost")
                .containsEntry("db.host", "localhost");
    }

    @Test
    void flattensAwsSecretJsonIntoPropertyMap() {
        Map<String, Object> properties = BootstrapPropertySupport.parseSecretPayload("""
                {
                  "SPRING_DATASOURCE_URL": "jdbc:postgresql://db.example/neondb",
                  "app": {
                    "jwt": {
                      "secret": "top-secret"
                    },
                    "cors": {
                      "allowedOrigins": ["https://a.example", "https://b.example"]
                    }
                  }
                }
                """);

        assertThat(properties)
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://db.example/neondb")
                .containsEntry("spring.datasource.url", "jdbc:postgresql://db.example/neondb")
                .containsEntry("app.jwt.secret", "top-secret")
                .containsEntry("APP_JWT_SECRET", "top-secret")
                .containsEntry("app.cors.allowedOrigins", "https://a.example,https://b.example")
                .containsEntry("APP_CORS_ALLOWED_ORIGINS", "https://a.example,https://b.example");
    }

    @Test
    void awsSecretsOverrideSystemEnvironmentButNotSystemProperties() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                        "JWT_SECRET", "legacy-env-secret"
                ))
        );
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, Map.of(
                        "app.jwt.secret", "system-property-secret"
                ))
        );

        BootstrapPropertySupport.addBeforeSystemEnvironment(
                environment,
                "awsSecretsManager",
                Map.of("JWT_SECRET", "aws-secret")
        );

        assertThat(environment.getProperty("JWT_SECRET")).isEqualTo("aws-secret");
        assertThat(environment.getProperty("app.jwt.secret")).isEqualTo("system-property-secret");
    }
}

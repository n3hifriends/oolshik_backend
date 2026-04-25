package com.oolshik.backend.config.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                  "SPRING_DATASOURCE_URL": "jdbc:postgresql://oolshik-db.abc123.ap-south-1.rds.amazonaws.com:5432/oolshik?sslmode=require",
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
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://oolshik-db.abc123.ap-south-1.rds.amazonaws.com:5432/oolshik?sslmode=require")
                .containsEntry("spring.datasource.url", "jdbc:postgresql://oolshik-db.abc123.ap-south-1.rds.amazonaws.com:5432/oolshik?sslmode=require")
                .containsEntry("app.jwt.secret", "top-secret")
                .containsEntry("APP_JWT_SECRET", "top-secret")
                .containsEntry("app.cors.allowedOrigins", "https://a.example,https://b.example")
                .containsEntry("APP_CORS_ALLOWED_ORIGINS", "https://a.example,https://b.example");
    }

    @Test
    void derivesDatasourcePropertiesFromRdsSecretShape() {
        Map<String, Object> properties = BootstrapPropertySupport.parseSecretPayload("""
                {
                  "engine": "postgres",
                  "host": "oolshik-dev-ap-south-1-rds.cabc123.ap-south-1.rds.amazonaws.com",
                  "port": 5432,
                  "dbname": "oolshik",
                  "username": "oolshik_admin",
                  "password": "super-secret"
                }
                """);

        assertThat(properties)
                .containsEntry("SPRING_DATASOURCE_URL", "jdbc:postgresql://oolshik-dev-ap-south-1-rds.cabc123.ap-south-1.rds.amazonaws.com:5432/oolshik?sslmode=require")
                .containsEntry("SPRING_DATASOURCE_USERNAME", "oolshik_admin")
                .containsEntry("SPRING_DATASOURCE_PASSWORD", "super-secret")
                .containsEntry("DB_HOST", "oolshik-dev-ap-south-1-rds.cabc123.ap-south-1.rds.amazonaws.com")
                .containsEntry("DB_NAME", "oolshik");
    }

    @Test
    void mapsAppSecretShortKeysToRuntimeProperties() {
        Map<String, Object> properties = BootstrapPropertySupport.parseSecretPayload("""
                {
                  "jwtSecret": "jwt-secret-value",
                  "googleClientId": "web-client-id.apps.googleusercontent.com",
                  "s3Bucket": "oolshik-dev-bucket",
                  "awsRegion": "ap-south-1",
                  "googleClientSecret": "stored-but-unused"
                }
                """);

        assertThat(properties)
                .containsEntry("JWT_SECRET", "jwt-secret-value")
                .containsEntry("APP_AUTH_GOOGLE_ALLOWED_CLIENT_IDS", "web-client-id.apps.googleusercontent.com")
                .containsEntry("MEDIA_S3_BUCKET", "oolshik-dev-bucket")
                .containsEntry("MEDIA_S3_REGION", "ap-south-1")
                .containsEntry("googleClientSecret", "stored-but-unused");
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

    @Test
    void resolvesLegacyAndSplitAwsSecretNames() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                        "app.secrets.aws.secretName", "legacy-one, legacy-two ",
                        "app.secrets.aws.dbSecretName", "oolshik/dev/db",
                        "app.secrets.aws.appSecretName", "oolshik/dev/app"
                ))
        );

        assertThat(AwsSecretsManagerEnvironmentPostProcessor.resolveSecretNames(environment))
                .isEqualTo(List.of("legacy-one", "legacy-two", "oolshik/dev/db", "oolshik/dev/app"));
    }
}

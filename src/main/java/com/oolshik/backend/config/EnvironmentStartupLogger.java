package com.oolshik.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnvironmentStartupLogger implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentStartupLogger.class);

    private final Environment env;

    public EnvironmentStartupLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String[] activeProfiles = env.getActiveProfiles();
        String profiles = activeProfiles.length > 0 ? String.join(",", activeProfiles) : "default";
        boolean isProdLike = isProdLike(activeProfiles);

        String dbMode = env.getProperty("APP_DB_MODE", env.getProperty("app.db.mode", "not-set"));
        String dbHost = extractDbHost(env.getProperty("spring.datasource.url", ""));
        String otpProvider = env.getProperty("app.otp.provider", "dev");
        String identityProvider = env.getProperty("app.security.identity-provider", "local");
        String kafkaEnabled = env.getProperty("app.messaging.kafka.enabled", "false");
        String mediaStorage = env.getProperty("media.storage", env.getProperty("MEDIA_STORAGE", "local"));
        boolean adminSeedEnabled = Boolean.parseBoolean(env.getProperty("app.admin.seed.enabled", "false"));
        String pushProvider = env.getProperty("app.admin.notification.pushProvider",
                env.getProperty("ADMIN_NOTIF_PUSH_PROVIDER", "DISABLED"));
        String corsOrigin0 = env.getProperty("app.cors.allowed-origins[0]",
                env.getProperty("app.cors.allowedOrigins[0]", "not-set"));

        log.info("=== OOLSHIK STARTUP ===");
        log.info("  profile          = {}", profiles);
        log.info("  dbMode           = {}", dbMode);
        log.info("  dbHost           = {}", dbHost);
        log.info("  otpProvider      = {}", otpProvider);
        log.info("  identityProvider = {}", identityProvider);
        log.info("  kafkaEnabled     = {}", kafkaEnabled);
        log.info("  mediaStorage     = {}", mediaStorage);
        log.info("  adminSeedEnabled = {}", adminSeedEnabled);
        log.info("  pushProvider     = {}", pushProvider);
        log.info("  cors[0]          = {}", corsOrigin0);
        log.info("=======================");

        if (isProdLike) {
            enforceProductionGuards(profiles, otpProvider, adminSeedEnabled, corsOrigin0, pushProvider);
        }
    }

    private void enforceProductionGuards(String profiles, String otpProvider, boolean adminSeedEnabled,
                                          String corsOrigin0, String pushProvider) {
        List<String> violations = new ArrayList<>();

        if ("dev".equalsIgnoreCase(otpProvider)) {
            violations.add("app.otp.provider=dev is not permitted in profile '" + profiles + "'");
        }
        if (adminSeedEnabled) {
            violations.add("app.admin.seed.enabled=true is not permitted in profile '" + profiles + "'");
        }
        if ("*".equals(corsOrigin0.trim())) {
            violations.add("CORS wildcard '*' is not permitted in profile '" + profiles + "'");
        }
        String jwtSecret = env.getProperty("JWT_SECRET", env.getProperty("app.jwt.secret", ""));
        if (!StringUtils.hasText(jwtSecret)
                || jwtSecret.startsWith("CHANGEME")
                || jwtSecret.startsWith("devsecret")) {
            violations.add("JWT_SECRET is missing or uses a development placeholder");
        }
        // FCM requires a valid service-account file on disk
        if ("FCM".equalsIgnoreCase(pushProvider)) {
            String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (!StringUtils.hasText(credPath) || !new File(credPath).exists()) {
                violations.add("ADMIN_NOTIF_PUSH_PROVIDER=FCM but GOOGLE_APPLICATION_CREDENTIALS is missing or does not exist at: " + credPath);
            }
        }

        if (!violations.isEmpty()) {
            violations.forEach(v -> log.error("UNSAFE CONFIG: {}", v));
            throw new IllegalStateException(
                    "Application refused to start: unsafe configuration in profile '" + profiles
                            + "'. See UNSAFE CONFIG errors above.");
        }
    }

    private static boolean isProdLike(String[] profiles) {
        for (String p : profiles) {
            if ("prod".equalsIgnoreCase(p) || "cloud-dev".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    private static String extractDbHost(String url) {
        if (!StringUtils.hasText(url)) return "unknown";
        try {
            // jdbc:postgresql://host:port/dbname?params → host:port
            String stripped = url.replaceFirst("^jdbc:[^:]+://", "");
            return stripped.split("/")[0].split("\\?")[0];
        } catch (Exception e) {
            return "unknown";
        }
    }
}

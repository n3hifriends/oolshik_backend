package com.oolshik.notificationworker.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "notification.fcmEnabled", havingValue = "true", matchIfMissing = false)
public class WorkerFirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkerFirebaseConfig.class);

    @Bean
    public FirebaseApp workerFirebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("Firebase already initialized, reusing default app");
            return FirebaseApp.getInstance();
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase initialized for notification-worker");
        return app;
    }
}

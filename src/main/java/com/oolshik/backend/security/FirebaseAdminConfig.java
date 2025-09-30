package com.oolshik.backend.security;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

// FirebaseAdminConfig.java
@Configuration
public class FirebaseAdminConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    @PostConstruct
    public void init() throws IOException {
        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (path == null || path.isBlank()) {
            log.warn("GOOGLE_APPLICATION_CREDENTIALS not set; skipping Firebase Admin init");
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            throw new FileNotFoundException("Firebase SA file not found at " + f.getAbsolutePath());
        }
        try (FileInputStream in = new FileInputStream(f)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            log.info("Initialized Firebase Admin with credentials at {}", f.getAbsolutePath());
        }
    }
}
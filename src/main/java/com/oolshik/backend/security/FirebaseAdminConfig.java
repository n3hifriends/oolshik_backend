package com.oolshik.backend.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.*;

@Configuration
public class FirebaseAdminConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseAdminConfig.class);

    @PostConstruct
    public void init() throws IOException {
        // Prefer ADC; falls back to explicit path if set
        String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (path == null || path.isBlank()) {
            GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
            initWith(creds, "ADC");
            return;
        }
        File f = new File(path);
        if (!f.exists()) throw new FileNotFoundException("Firebase SA file not found at " + f.getAbsolutePath());
        try (FileInputStream in = new FileInputStream(f)) {
            GoogleCredentials creds = GoogleCredentials.fromStream(in);
            initWith(creds, f.getAbsolutePath());
        }
    }

    private void initWith(GoogleCredentials creds, String source) throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions opts = FirebaseOptions.builder().setCredentials(creds).build();
            FirebaseApp.initializeApp(opts);
            log.info("Initialized Firebase Admin using {}", source);
        }
    }
}
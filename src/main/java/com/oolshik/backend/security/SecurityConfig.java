package com.oolshik.backend.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Value("${firebase.project-id}")
    private String firebaseProjectId;

    @Value("${firebase.check-revoked:false}")
    private boolean checkRevoked;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            FirebaseApp.initializeApp(options);
        }
        return FirebaseAuth.getInstance();
    }

    @Bean
    public FirebaseTokenFilter firebaseTokenFilter(FirebaseAuth firebaseAuth) {
        return new FirebaseTokenFilter(firebaseAuth, firebaseProjectId, checkRevoked);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            FirebaseTokenFilter firebaseTokenFilter
            // , JwtAuthFilter jwtAuthFilter   // <= Only if you still use your own HS256 tokens on *separate* endpoints
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/actuator/**",
                                "/swagger/**",
                                "/v3/api-docs/**",
                                "/api/public/**",
                                "/api/auth/echo"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Admin SDK verification for Firebase ID tokens
                .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class)
                // If you *must* keep JwtAuthFilter, only add it for endpoints that never carry Firebase tokens
                // .addFilterAfter(jwtAuthFilter, FirebaseTokenFilter.class)
                .cors(Customizer.withDefaults());

        return http.build();
    }
}
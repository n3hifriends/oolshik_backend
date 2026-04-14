package com.oolshik.backend.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(com.oolshik.backend.config.CorsProperties.class)
public class SecurityConfig {

    @Value("${firebase.project-id}")
    private String firebaseProjectId;

    @Value("${firebase.check-revoked:false}")
    private boolean checkRevoked;

    @Bean
    @Conditional(FirebaseIdentityCondition.class)
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
    @Conditional(FirebaseIdentityCondition.class)
    public FirebaseTokenFilter firebaseTokenFilter(FirebaseAuth firebaseAuth) {
        return new FirebaseTokenFilter(firebaseAuth, firebaseProjectId, checkRevoked);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectProvider<FirebaseTokenFilter> firebaseTokenFilterProvider,
            JwtAuthFilter jwtAuthFilter
    ) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), "Forbidden"))
                )
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(
                                "/actuator/health/**",
                                "/swagger/**",
                                "/v3/api-docs/**",
                                "/api/public/**",
                                "/api/auth/echo",
                                "/api/auth/otp/**",
                                "/api/auth/google",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .cors(Customizer.withDefaults());

        FirebaseTokenFilter firebaseTokenFilter = firebaseTokenFilterProvider.getIfAvailable();
        if (firebaseTokenFilter != null) {
            http.addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);
        }
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

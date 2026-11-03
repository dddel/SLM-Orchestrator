package com.tirocinioaltrama.slmorchestrator.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final List<String> STRICT_LOCAL_ORIGINS = List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost:8080",
            "http://127.0.0.1:8080"
    );

    @Value("${orchestrator.cors.allowed-origins:}")
    private String customAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origini rigorosamente limitate a localhost e 127.0.0.1 (o property esplicita)
        if (customAllowedOrigins != null && !customAllowedOrigins.isBlank()) {
            List<String> origins = Arrays.stream(customAllowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            configuration.setAllowedOrigins(origins);
        } else {
            configuration.setAllowedOrigins(STRICT_LOCAL_ORIGINS);
        }

        // Metodi HTTP strettamente limitati a quelli esposti
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));

        // Header consentiti rigorosamente limitati
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));

        // Disabilitazione trasmissione credenziali per prevenire CSRF e attacchi cross-site
        configuration.setAllowCredentials(false);

        // Cache del preflight OPTIONS (1 ora) per ottimizzare le prestazioni locali
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

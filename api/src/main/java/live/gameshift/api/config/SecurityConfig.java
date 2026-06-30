package live.gameshift.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Health checks are public for ALB target group probes
                .requestMatchers("/actuator/health", "/api/health").permitAll()

                // SSE stream, summaries, and subscriptions are public
                // (frontend connects without Bearer token for real-time data)
                .requestMatchers("/api/events/**", "/api/summaries/**", "/api/subscriptions/**", "/api/sports/**", "/api/fixtures/**").permitAll()

                // All other API endpoints require a valid Cognito JWT
                .requestMatchers("/api/**").authenticated()

                .anyRequest().authenticated()
            )
            // Validate Cognito JWTs using the issuer URI from application.properties
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}

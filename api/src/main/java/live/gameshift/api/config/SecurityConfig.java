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
            .cors(Customizer.withDefaults()) // Enable CORS (using the config we build next)
            .csrf(csrf -> csrf.disable())    // CSRF is not needed for stateless REST APIs
            .authorizeHttpRequests(auth -> auth
                // Allow health checks so the AWS Load Balancer doesn't think our app is dead!
                .requestMatchers("/actuator/health", "/api/health").permitAll()
                
                // IMPORTANT: For this Phase, we are making the API public so you can test it 
                // in your browser DevTools without needing a Cognito Token. 
                // In Phase 5, we will change this to .authenticated() to lock it down!
                .requestMatchers("/api/**").permitAll()
                
                .anyRequest().authenticated()
            );
            // If we use Cognito JWTs later, this single line enables JWT validation!
            // .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}

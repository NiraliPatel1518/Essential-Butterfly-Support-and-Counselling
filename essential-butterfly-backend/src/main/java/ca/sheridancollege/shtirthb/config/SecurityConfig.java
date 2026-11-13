
package ca.sheridancollege.shtirthb.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import io.jsonwebtoken.security.Keys;

@Configuration
public class SecurityConfig {

    private final AppProperties properties;

    public SecurityConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {

        String secret = properties.getJwt().getSecret();

        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 48) {

            throw new IllegalStateException(
                    "JWT secret must contain at least 48 UTF-8 bytes."
            );
        }

        SecretKey key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS384)
                .build();

        OAuth2TokenValidator<Jwt> adminMfaValidator = jwt -> {

            Object role = jwt.getClaims().get("role");

            if ("ADMIN".equalsIgnoreCase(String.valueOf(role))
                    && !Boolean.TRUE.equals(
                            jwt.getClaims().get("mfa"))) {

                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Administrator two-factor verification is required.",
                                null
                        )
                );
            }

            return OAuth2TokenValidatorResult.success();
        };

        // Reject pre-2FA admin JWTs as well as any admin JWT without MFA.
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefault(),
                        adminMfaValidator
                )
        );

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .cors(Customizer.withDefaults())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth

                // Public feature visibility
                .requestMatchers(HttpMethod.GET, "/api/features")
                    .permitAll()

                // Public website updates
                .requestMatchers(HttpMethod.GET, "/api/updates")
                    .permitAll()

                // Public website content (published copy only)
                .requestMatchers(HttpMethod.GET, "/api/content/*")
                    .permitAll()

                // Public Resource Library (published resources and files only)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/resources",
                    "/api/resources/*/download"
                ).permitAll()

                // Public Memory Wall (published photos only)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/memory-wall",
                    "/api/memory-wall/photos/*/image"
                ).permitAll()

                // Public appointment availability (open slots only, no
                // client or booking information)
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/appointments/availability"
                ).permitAll()

                // Booking and viewing one's own appointments: clients only
                .requestMatchers("/api/appointments/**")
                    .hasRole("CLIENT")

                // Public testimonials
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/testimonials/public",
                    "/api/testimonials/featured"
                ).permitAll()

                // Public newsletter subscription
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/newsletter/subscribe"
                ).permitAll()

                // Public newsletter unsubscribe
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/newsletter/unsubscribe"
                ).permitAll()

                // Public authentication, password reset and contact
                .requestMatchers(
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/verify-email",
                    "/api/auth/resend-verification",
                    "/api/password/forgot",
                    "/api/password/reset",
                    "/api/contact"
                ).permitAll()

                // Admin login OTP endpoints.
                // Access is controlled by the opaque challenge token.
                .requestMatchers("/api/admin-auth/**").permitAll()

                // Admin account settings require authenticated ADMIN.
                .requestMatchers("/api/admin-account/**")
                    .hasRole("ADMIN")

                // Existing admin endpoints
                .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                // Full update content requires authentication
                .requestMatchers(HttpMethod.GET, "/api/updates/**")
                    .authenticated()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                )
            )

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(formLogin -> formLogin.disable());

        return http.build();
    }
}

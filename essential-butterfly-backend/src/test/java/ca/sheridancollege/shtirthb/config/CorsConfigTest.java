package ca.sheridancollege.shtirthb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class CorsConfigTest {

    private CorsConfiguration buildFrom(AppProperties properties) {
        CorsConfigurationSource source = new CorsConfig(properties).corsConfigurationSource();
        assertTrue(source instanceof UrlBasedCorsConfigurationSource);
        return ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations().get("/**");
    }

    @Test
    void appliesTheConfiguredCorsRulesToEveryPath() {
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowedOrigins(List.of("http://localhost:5173"));
        properties.getCors().setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        properties.getCors().setAllowedHeaders(List.of("Authorization", "Content-Type"));
        properties.getCors().setAllowCredentials(true);

        CorsConfiguration configuration = buildFrom(properties);

        assertNotNull(configuration);
        assertEquals(List.of("http://localhost:5173"), configuration.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "DELETE"), configuration.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type"), configuration.getAllowedHeaders());
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
    }

    @Test
    void allowsOnlyTheListedOrigin() {
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowedOrigins(List.of("http://localhost:5173"));
        properties.getCors().setAllowedMethods(List.of("GET"));
        properties.getCors().setAllowedHeaders(List.of("*"));

        CorsConfiguration configuration = buildFrom(properties);

        assertEquals("http://localhost:5173",
                configuration.checkOrigin("http://localhost:5173"));
        assertEquals(null, configuration.checkOrigin("https://evil.example.com"));
    }

    @Test
    void canTurnCredentialsOff() {
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowCredentials(false);

        assertEquals(Boolean.FALSE, buildFrom(properties).getAllowCredentials());
    }
}
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

    /*
     * Checks: The CORS rules from the settings are applied to every URL (/**).
     * How:    We set one origin, four methods, two headers and credentials on. We build the CorsConfig
     *   and read the rule for /**. It must contain exactly those values.
     */
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

    /*
     * Checks: Only the listed website address is allowed to call the API.
     * How:    checkOrigin returns the origin when it is listed (localhost:5173) and null for an
     *   unknown one (evil.example.com), so other sites are blocked.
     */
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

    /*
     * Checks: Sending cookies and credentials can be turned off.
     * How:    We set allowCredentials to false and check the built rule says false.
     */
    @Test
    void canTurnCredentialsOff() {
        AppProperties properties = new AppProperties();
        properties.getCors().setAllowCredentials(false);

        assertEquals(Boolean.FALSE, buildFrom(properties).getAllowCredentials());
    }
}
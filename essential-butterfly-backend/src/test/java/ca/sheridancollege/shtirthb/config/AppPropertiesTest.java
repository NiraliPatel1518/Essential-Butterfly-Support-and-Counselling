package ca.sheridancollege.shtirthb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    /*
     * Checks: A new AppProperties object starts with safe, empty defaults.
     * How:    We create the settings object with nothing configured. The CORS lists are empty,
     *   credentials are allowed by default, and the secret, mail address and base URLs are null,
     *   so nothing is open by accident.
     */
    @Test
    void hasSafeDefaultsWhenNothingIsConfigured() {
        AppProperties properties = new AppProperties();

        assertTrue(properties.getCors().getAllowedOrigins().isEmpty());
        assertTrue(properties.getCors().getAllowedMethods().isEmpty());
        assertTrue(properties.getCors().getAllowedHeaders().isEmpty());
        assertTrue(properties.getCors().isAllowCredentials());
        assertNull(properties.getJwt().getSecret());
        assertNull(properties.getMail().getFromAddress());
        assertNull(properties.getFrontendBaseUrl());
        assertNull(properties.getPublicBaseUrl());
    }

    /*
     * Checks: Every setting that starts with app. is read into the AppProperties object.
     * How:    ApplicationContextRunner starts a tiny Spring context with test values such as
     *   app.cors.allowed-origins and app.jwt.secret. We then read the bean and check each value,
     *   including lists split by commas.
     */
    @Test
    void bindsAllValuesFromTheAppPrefix() {
        runner.withPropertyValues(
                "app.cors.allowed-origins=http://localhost:5173,https://example.com",
                "app.cors.allowed-methods=GET,POST",
                "app.cors.allowed-headers=Authorization,Content-Type",
                "app.cors.allow-credentials=false",
                "app.jwt.secret=test-secret-value",
                "app.mail.from-address=no-reply@example.com",
                "app.frontend-base-url=http://localhost:5173",
                "app.public-base-url=http://localhost:8080"
        ).run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);

            assertEquals(List.of("http://localhost:5173", "https://example.com"),
                    properties.getCors().getAllowedOrigins());
            assertEquals(List.of("GET", "POST"), properties.getCors().getAllowedMethods());
            assertEquals(List.of("Authorization", "Content-Type"),
                    properties.getCors().getAllowedHeaders());
            assertEquals(false, properties.getCors().isAllowCredentials());
            assertEquals("test-secret-value", properties.getJwt().getSecret());
            assertEquals("no-reply@example.com", properties.getMail().getFromAddress());
            assertEquals("http://localhost:5173", properties.getFrontendBaseUrl());
            assertEquals("http://localhost:8080", properties.getPublicBaseUrl());
        });
    }

    /*
     * Checks: AppProperties is created as exactly one Spring bean.
     * How:    We start the small context and count the beans of this type. There must be exactly 1.
     */
    @Test
    void isRegisteredAsABeanWhenEnabled() {
        runner.run(context ->
                assertEquals(1, context.getBeansOfType(AppProperties.class).size()));
    }
}
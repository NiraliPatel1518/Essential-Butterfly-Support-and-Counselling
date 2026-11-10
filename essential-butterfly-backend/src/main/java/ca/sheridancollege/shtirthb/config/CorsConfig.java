
package ca.sheridancollege.shtirthb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private final AppProperties properties;

    public CorsConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        AppProperties.Cors corsProperties = properties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                corsProperties.getAllowedOrigins()
        );

        configuration.setAllowedMethods(
                corsProperties.getAllowedMethods()
        );

        configuration.setAllowedHeaders(
                corsProperties.getAllowedHeaders()
        );

        configuration.setAllowCredentials(
                corsProperties.isAllowCredentials()
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

package ca.sheridancollege.shtirthb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ca.sheridancollege.shtirthb.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EssentialButterflyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EssentialButterflyBackendApplication.class, args);
    }

}

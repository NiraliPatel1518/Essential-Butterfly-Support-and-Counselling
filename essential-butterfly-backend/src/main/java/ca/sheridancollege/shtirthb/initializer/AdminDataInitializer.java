package ca.sheridancollege.shtirthb.initializer;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

@Configuration
public class AdminDataInitializer {

    @Bean
    CommandLineRunner createDefaultAdmin(
            AdminUserRepository adminUserRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            String email = "admin@test.com";

            if (!adminUserRepository.existsByEmail(email)) {

                AdminUser admin = new AdminUser(
                        "Administrator",
                        email,
                        passwordEncoder.encode("Admin@12345")
                );

                adminUserRepository.save(admin);

                System.out.println(
                        "Default admin account created: " + email
                );
            }
        };
    }
} 
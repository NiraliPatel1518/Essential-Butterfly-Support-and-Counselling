package ca.sheridancollege.shtirthb.initializer;

import org.springframework.beans.factory.annotation.Value;
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
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {

        return args -> {

            // The admin email and password come from the environment.
            // They are never written in the code.
            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                System.out.println(
                        "No admin account created. Set ADMIN_EMAIL and "
                        + "ADMIN_PASSWORD to create one."
                );
                return;
            }

            // Login lowercases the email, so we save it the same way.
            String email = adminEmail.trim().toLowerCase();

            /*
             * Only create the initial admin when the table is empty.
             *
             * Do not recreate admin@test.com merely because the
             * administrator changed their registered email address.
             */
            if (adminUserRepository.count() == 0) {

                AdminUser admin = new AdminUser(
                        "Administrator",
                        email,
                        passwordEncoder.encode(adminPassword)
                );

                adminUserRepository.save(admin);

                System.out.println(
                        "Admin account created: " + email
                );
            }
        };
    }
}
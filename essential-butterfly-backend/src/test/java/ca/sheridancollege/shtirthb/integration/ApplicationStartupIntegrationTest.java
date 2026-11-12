package ca.sheridancollege.shtirthb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

import ca.sheridancollege.shtirthb.config.AppProperties;
import ca.sheridancollege.shtirthb.exception.GlobalExceptionHandler;
import ca.sheridancollege.shtirthb.model.AdminUser;
import ca.sheridancollege.shtirthb.repository.AdminUserRepository;

/*
 * Starts the whole application against a real PostgreSQL database.
 * The "jdbc:tc:" URL makes Testcontainers start a temporary PostgreSQL
 * container in Docker and remove it when the tests finish.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///integration_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890",
        "app.admin.email=admin@test.com",
        "app.admin.password=Admin@12345"
})
class ApplicationStartupIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminUserRepository adminUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void theWholeApplicationStarts() {
        assertNotNull(context.getBean(AppProperties.class));
        assertNotNull(context.getBean("corsConfigurationSource", CorsConfigurationSource.class));
        assertNotNull(context.getBean(GlobalExceptionHandler.class));
    }

    @Test
    void usesARealPostgreSqlDatabase() {
        String version = jdbc.queryForObject("select version()", String.class);

        assertNotNull(version);
        assertTrue(version.contains("PostgreSQL"), version);
    }

    @Test
    void createsTheTablesForAllEntities() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertTrue(tables.contains("admin_users"), tables.toString());
        assertTrue(tables.size() >= 11, "Expected at least 11 tables but found " + tables);
    }

    @Test
    void createsTheDefaultAdminWithAHashedPassword() {
        AdminUser admin = adminUsers.findByEmail("admin@test.com").orElseThrow();

        assertEquals("Administrator", admin.getFullName());
        assertNotEquals("Admin@12345", admin.getPassword());
        assertTrue(passwordEncoder.matches("Admin@12345", admin.getPassword()));
    }

    @Test
    void databaseRejectsTwoAdminsWithTheSameEmail() {
        adminUsers.save(new AdminUser("First", "same@example.com", "hash-one"));

        assertThrows(DataIntegrityViolationException.class, () ->
                adminUsers.save(new AdminUser("Second", "same@example.com", "hash-two")));
    }
}
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
        "spring.jpa.hibernate.ddl-auto=validate",
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

    /*
     * Checks: The whole application starts with a real database.
     * How:    SpringBootTest starts everything. We then ask the context for three important beans
     *   (AppProperties, the CORS source and the global exception handler). None may be null.
     */
    @Test
    void theWholeApplicationStarts() {
        assertNotNull(context.getBean(AppProperties.class));
        assertNotNull(context.getBean("corsConfigurationSource", CorsConfigurationSource.class));
        assertNotNull(context.getBean(GlobalExceptionHandler.class));
    }

    /*
     * Checks: The test database is a real PostgreSQL, not a fake one.
     * How:    We run select version() with JdbcTemplate. The answer must contain the word PostgreSQL.
     */
    @Test
    void usesARealPostgreSqlDatabase() {
        String version = jdbc.queryForObject("select version()", String.class);

        assertNotNull(version);
        assertTrue(version.contains("PostgreSQL"), version);
    }

    /*
     * Checks: The tables for all our data are created.
     * How:    We list the tables in the public schema. admin_users must be there, and there must be at
     *   least 11 tables.
     */
    @Test
    void createsTheTablesForAllEntities() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertTrue(tables.contains("admin_users"), tables.toString());
        assertTrue(tables.size() >= 11, "Expected at least 11 tables but found " + tables);
    }

    /*
     * Checks: The default admin is created at start-up with a hashed password.
     * How:    We find admin@test.com in the real database. The saved password must be different from
     *   Admin@12345, and the BCrypt encoder must say it matches.
     */
    @Test
    void createsTheDefaultAdminWithAHashedPassword() {
        AdminUser admin = adminUsers.findByEmail("admin@test.com").orElseThrow();

        assertEquals("Administrator", admin.getFullName());
        assertNotEquals("Admin@12345", admin.getPassword());
        assertTrue(passwordEncoder.matches("Admin@12345", admin.getPassword()));
    }

    /*
     * Checks: The database refuses two admins with the same email.
     * How:    We save one admin, then a second one with the same email. The second save must throw
     *   DataIntegrityViolationException (the database unique rule works).
     */
    @Test
    void databaseRejectsTwoAdminsWithTheSameEmail() {
        adminUsers.saveAndFlush(new AdminUser("First", "same@example.com", "hash-one"));

        assertThrows(DataIntegrityViolationException.class, () ->
                adminUsers.saveAndFlush(new AdminUser("Second", "same@example.com", "hash-two")));
    }
}
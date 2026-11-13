package ca.sheridancollege.shtirthb.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/*
 * Shared setup for repository tests. The whole application runs against a
 * temporary PostgreSQL container. Every test runs inside a transaction that
 * is rolled back at the end, so tests never see each other's data.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///repository_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234567890"
})
@Transactional
abstract class RepositoryIntegrationTestBase {
}
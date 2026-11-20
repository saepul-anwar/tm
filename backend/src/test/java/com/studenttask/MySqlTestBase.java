package com.studenttask;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all integration tests. A single MySQL 8 container is shared by the whole test JVM
 * (started once, removed by Testcontainers when the JVM exits). All values below are synthetic.
 */
@SpringBootTest
public abstract class MySqlTestBase {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8")).withDatabaseName("studenttask_test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.jwt.secret", () -> "test-only-synthetic-jwt-secret-0123456789abcdef");
        registry.add("app.telegram.bot-token", () -> "test-only-synthetic-bot-token");
        registry.add("app.telegram.webhook-secret", () -> "test-only-synthetic-webhook-secret");
        registry.add("app.telegram.bot-username", () -> "test_only_bot");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }
}

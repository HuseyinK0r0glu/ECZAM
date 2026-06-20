package com.eczam;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    /**
     * Singleton container pattern: start once per JVM and never stop it explicitly.
     * Spring caches the ApplicationContext (same config) across integration test
     * classes, so the DB it points at must outlive any single test class. A
     * {@code @Container}/{@code @Testcontainers}-managed container would be torn
     * down after each class, leaving the cached context pointing at a dead port.
     * Testcontainers' Ryuk reaper removes this container when the JVM exits.
     */
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("eczam").withUsername("eczam").withPassword("eczam");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("eczam.jwt.secret", () -> "test-secret-test-secret-test-secret-32bytes!!");
    }
}

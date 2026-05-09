package pl.aibprojekt.couponservice.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.aibprojekt.couponservice.CouponServiceApplication;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Testcontainers
@ContextConfiguration(classes = CouponServiceApplication.class)
class CouponRepositoryCustomImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        // Flyway is included in @DataR2dbcTest slice — configure its JDBC URL or disable it.
        // Schema setup is done manually in @BeforeEach so Flyway is not needed here.
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private DatabaseClient databaseClient;

    private CouponRepositoryCustomImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CouponRepositoryCustomImpl(databaseClient);
        // Create table schema manually (Flyway doesn't run in @DataR2dbcTest slice)
        databaseClient.sql("CREATE TABLE IF NOT EXISTS coupons (" +
            "code VARCHAR(64) PRIMARY KEY, " +
            "max_uses INTEGER NOT NULL, " +
            "current_uses INTEGER NOT NULL DEFAULT 0, " +
            "country CHAR(2) NOT NULL, " +
            "per_user_limit BOOLEAN NOT NULL DEFAULT false, " +
            "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW())")
            .fetch().rowsUpdated().block();
        // Clean up between tests
        databaseClient.sql("DELETE FROM coupons").fetch().rowsUpdated().block();
    }

    @Test
    void atomicIncrementUsage_shouldReturnRemainingUses_whenCouponHasCapacity() {
        // Given — maxUses=5, currentUses=0; after increment: remaining = 5-1 = 4
        databaseClient.sql("INSERT INTO coupons (code, max_uses, current_uses, country, per_user_limit) VALUES ('TEST1', 5, 0, 'PL', false)")
            .fetch().rowsUpdated().block();

        // When / Then
        StepVerifier.create(repository.atomicIncrementUsage("TEST1"))
            .expectNext(4)
            .verifyComplete();
    }

    @Test
    void atomicIncrementUsage_shouldReturnEmptyMono_whenCouponExhausted() {
        // Given — cap already reached; WHERE condition fails, RETURNING yields no row
        databaseClient.sql("INSERT INTO coupons (code, max_uses, current_uses, country, per_user_limit) VALUES ('TEST2', 2, 2, 'PL', false)")
            .fetch().rowsUpdated().block();

        // When / Then
        StepVerifier.create(repository.atomicIncrementUsage("TEST2"))
            .verifyComplete();
    }

    @Test
    void atomicIncrementUsage_shouldReturnEmptyMono_whenCodeNotFound() {
        // When / Then — no matching row, RETURNING yields no row
        StepVerifier.create(repository.atomicIncrementUsage("NONEXISTENT"))
            .verifyComplete();
    }
}

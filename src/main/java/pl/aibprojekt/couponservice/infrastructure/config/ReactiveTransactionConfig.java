package pl.aibprojekt.couponservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class ReactiveTransactionConfig {

    // Spring Boot does not auto-configure TransactionalOperator — only R2dbcTransactionManager.
    // CouponServiceImpl uses the operator directly for explicit, minimal transaction scoping
    // (wrapping only the atomic UPDATE + INSERT, not the upstream geo HTTP call).
    @Bean
    public TransactionalOperator transactionalOperator(R2dbcTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}

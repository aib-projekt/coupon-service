package pl.aibprojekt.couponservice.infrastructure.persistence;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class CouponRepositoryCustomImpl implements CouponRepositoryCustom {

    // RETURNING yields the post-increment remaining count directly from the DB,
    // avoiding stale snapshot calculations in the application layer.
    private static final String ATOMIC_INCREMENT_SQL =
        "UPDATE coupons SET current_uses = current_uses + 1 " +
        "WHERE code = :code AND current_uses < max_uses " +
        "RETURNING max_uses - current_uses AS remaining_uses";

    private final DatabaseClient databaseClient;

    public CouponRepositoryCustomImpl(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Integer> atomicIncrementUsage(String couponCode) {
        return databaseClient.sql(ATOMIC_INCREMENT_SQL)
            .bind("code", couponCode)
            .map((row, meta) -> row.get("remaining_uses", Integer.class))
            .one();
        // Returns empty Mono if no row matched (code not found or cap already reached)
    }
}

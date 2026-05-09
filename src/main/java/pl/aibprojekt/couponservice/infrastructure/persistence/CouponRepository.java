package pl.aibprojekt.couponservice.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import pl.aibprojekt.couponservice.domain.Coupon;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface CouponRepository extends R2dbcRepository<Coupon, String>, CouponRepositoryCustom {

    /**
     * Inserts a new coupon using a raw SQL INSERT to avoid Spring Data R2DBC
     * treating the entity as existing (since String IDs are never null).
     * Returns the inserted coupon via a subsequent findById lookup.
     */
    @Query("INSERT INTO coupons (code, max_uses, current_uses, country, per_user_limit, created_at) " +
           "VALUES (:code, :maxUses, 0, :country, :perUserLimit, :createdAt)")
    Mono<Void> insertCoupon(String code, int maxUses, String country, boolean perUserLimit, Instant createdAt);
}

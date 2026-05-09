package pl.aibprojekt.couponservice.infrastructure.persistence;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import pl.aibprojekt.couponservice.domain.CouponUsage;
import reactor.core.publisher.Mono;

public interface CouponUsageRepository extends R2dbcRepository<CouponUsage, Long> {
    Mono<Boolean> existsByCouponCodeAndUserId(String couponCode, String userId);
}

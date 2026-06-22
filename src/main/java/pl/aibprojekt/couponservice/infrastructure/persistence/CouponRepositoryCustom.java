package pl.aibprojekt.couponservice.infrastructure.persistence;

import reactor.core.publisher.Mono;

public interface CouponRepositoryCustom {
    /**
     * Atomically increments current_uses if current_uses < max_uses.
     * Returns remaining uses (max_uses - current_uses) after the increment,
     * or empty Mono if the cap is already reached or the code was not found.
     */
    Mono<Integer> atomicIncrementUsage(String couponCode);
}

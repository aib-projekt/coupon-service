package pl.aibprojekt.couponservice.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import pl.aibprojekt.couponservice.api.dto.CouponResponse;
import pl.aibprojekt.couponservice.api.dto.CreateCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedeemCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedemptionResponse;
import pl.aibprojekt.couponservice.domain.Coupon;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import pl.aibprojekt.couponservice.domain.CouponUsage;
import pl.aibprojekt.couponservice.infrastructure.persistence.CouponRepository;
import pl.aibprojekt.couponservice.infrastructure.persistence.CouponUsageRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final GeoLocationService geoLocationService;
    private final TransactionalOperator transactionalOperator;

    public CouponServiceImpl(CouponRepository couponRepository,
                             CouponUsageRepository couponUsageRepository,
                             GeoLocationService geoLocationService,
                             TransactionalOperator transactionalOperator) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.geoLocationService = geoLocationService;
        this.transactionalOperator = transactionalOperator;
    }

    @Override
    public Mono<CouponResponse> createCoupon(CreateCouponRequest request) {
        String code = request.code().toUpperCase();
        String country = request.country().toUpperCase();
        Instant createdAt = Instant.now();

        return couponRepository.insertCoupon(code, request.maxUses(), country, request.perUserLimit(), createdAt)
            .then(Mono.defer(() -> couponRepository.findById(code)))
            .map(this::toCouponResponse)
            .onErrorMap(DataIntegrityViolationException.class,
                ex -> new CouponException(CouponErrorCode.COUPON_ALREADY_EXISTS,
                    "Coupon with code '" + code + "' already exists."));
    }

    @Override
    public Mono<CouponResponse> getCoupon(String code) {
        return couponRepository.findById(code.toUpperCase())
            .switchIfEmpty(Mono.error(new CouponException(CouponErrorCode.COUPON_NOT_FOUND,
                "Coupon '" + code.toUpperCase() + "' not found.")))
            .map(this::toCouponResponse);
    }

    @Override
    public Mono<RedemptionResponse> redeemCoupon(String code, String clientIp, RedeemCouponRequest request) {
        String normalizedCode = code.toUpperCase();

        return couponRepository.findById(normalizedCode)
            .switchIfEmpty(Mono.error(new CouponException(CouponErrorCode.COUPON_NOT_FOUND,
                "Coupon '" + normalizedCode + "' not found.")))
            .flatMap(coupon ->
                geoLocationService.getCountryCode(clientIp)
                    .onErrorMap(ex -> !(ex instanceof CouponException),
                        ex -> new CouponException(CouponErrorCode.GEO_UNAVAILABLE,
                            "Geolocation service is unavailable."))
                    .flatMap(countryCode -> {
                        if (!countryCode.equalsIgnoreCase(coupon.country())) {
                            return Mono.error(new CouponException(CouponErrorCode.COUNTRY_NOT_ALLOWED,
                                "Coupon is not valid in your country."));
                        }
                        return validateUserId(coupon, request)
                            .then(Mono.defer(() -> executeAtomicRedeem(coupon, request, normalizedCode)));
                    })
            );
    }

    private Mono<Void> validateUserId(Coupon coupon, RedeemCouponRequest request) {
        if (!coupon.perUserLimit()) {
            return Mono.empty();
        }
        String userId = request != null ? request.userId() : null;
        if (userId == null || userId.isBlank()) {
            return Mono.error(new CouponException(CouponErrorCode.INVALID_REQUEST,
                "userId is required for this coupon."));
        }
        // Pre-existence check removed: TOCTOU race. The DB UNIQUE constraint inside
        // the transaction is the sole authority for ALREADY_USED detection.
        return Mono.empty();
    }

    private Mono<RedemptionResponse> executeAtomicRedeem(Coupon coupon, RedeemCouponRequest request, String normalizedCode) {
        Mono<RedemptionResponse> transactionalFlow = couponRepository.atomicIncrementUsage(normalizedCode)
            // Empty Mono means no row matched: either exhausted or not found (not found already
            // guarded above, so here it always means exhausted)
            .switchIfEmpty(Mono.error(new CouponException(CouponErrorCode.COUPON_EXHAUSTED,
                "Coupon has reached its maximum number of uses.")))
            .flatMap(remainingUses -> {
                Mono<Void> insertUsage = Mono.empty();
                if (coupon.perUserLimit() && request != null && request.userId() != null) {
                    CouponUsage usage = new CouponUsage(null, normalizedCode, request.userId(), Instant.now());
                    insertUsage = couponUsageRepository.save(usage)
                        .onErrorMap(DataIntegrityViolationException.class,
                            ex -> new CouponException(CouponErrorCode.ALREADY_USED,
                                "This coupon has already been used by this user."))
                        .then();
                }
                return insertUsage.thenReturn(new RedemptionResponse(normalizedCode, remainingUses, Instant.now()));
            });

        return transactionalOperator.transactional(transactionalFlow);
    }

    private CouponResponse toCouponResponse(Coupon coupon) {
        return new CouponResponse(
            coupon.code(), coupon.maxUses(), coupon.currentUses(),
            coupon.country(), coupon.perUserLimit(), coupon.createdAt()
        );
    }
}

package pl.aibprojekt.couponservice.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.reactive.TransactionalOperator;
import pl.aibprojekt.couponservice.api.dto.CreateCouponRequest;
import pl.aibprojekt.couponservice.api.dto.RedeemCouponRequest;
import pl.aibprojekt.couponservice.domain.Coupon;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import pl.aibprojekt.couponservice.infrastructure.persistence.CouponRepository;
import pl.aibprojekt.couponservice.infrastructure.persistence.CouponUsageRepository;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponUsageRepository couponUsageRepository;
    @Mock private GeoLocationService geoLocationService;
    @Mock private TransactionalOperator transactionalOperator;

    @InjectMocks
    private CouponServiceImpl couponService;

    private static final Coupon COUPON_PL = new Coupon("SPRING20", 10, 0, "PL", false, Instant.now());
    private static final Coupon COUPON_PL_PER_USER = new Coupon("SPRING20", 10, 0, "PL", true, Instant.now());

    @Test
    void createCoupon_shouldReturnCouponResponse_whenSuccess() {
        // Given
        CreateCouponRequest request = new CreateCouponRequest("spring20", 10, "PL", false);
        Coupon saved = new Coupon("SPRING20", 10, 0, "PL", false, Instant.now());
        when(couponRepository.insertCoupon(eq("SPRING20"), eq(10), eq("PL"), eq(false), any(Instant.class)))
            .thenReturn(Mono.empty());
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(saved));

        // When / Then
        StepVerifier.create(couponService.createCoupon(request))
            .assertNext(r -> assertThat(r.code()).isEqualTo("SPRING20"))
            .verifyComplete();
    }

    @Test
    void createCoupon_shouldThrowCouponAlreadyExists_whenDuplicateCode() {
        // Given
        CreateCouponRequest request = new CreateCouponRequest("SPRING20", 10, "PL", false);
        when(couponRepository.insertCoupon(anyString(), anyInt(), anyString(), anyBoolean(), any()))
            .thenReturn(Mono.error(new DataIntegrityViolationException("dup")));

        // When / Then
        StepVerifier.create(couponService.createCoupon(request))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.COUPON_ALREADY_EXISTS)
            .verify();
    }

    @Test
    void getCoupon_shouldReturnCouponResponse_whenFound() {
        // Given
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL));

        // When / Then
        StepVerifier.create(couponService.getCoupon("spring20"))
            .assertNext(r -> assertThat(r.code()).isEqualTo("SPRING20"))
            .verifyComplete();
    }

    @Test
    void getCoupon_shouldThrowCouponNotFound_whenNotFound() {
        // Given
        when(couponRepository.findById("UNKNOWN")).thenReturn(Mono.empty());

        // When / Then
        StepVerifier.create(couponService.getCoupon("UNKNOWN"))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.COUPON_NOT_FOUND)
            .verify();
    }

    @Test
    void redeemCoupon_shouldReturnRedemptionResponse_whenSuccess() {
        // Given — atomicIncrementUsage returns remaining uses (DB RETURNING value)
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL));
        when(geoLocationService.getCountryCode("1.2.3.4")).thenReturn(Mono.just("PL"));
        when(couponRepository.atomicIncrementUsage("SPRING20")).thenReturn(Mono.just(9));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest(null)))
            .assertNext(r -> {
                assertThat(r.code()).isEqualTo("SPRING20");
                assertThat(r.remainingUses()).isEqualTo(9);
            })
            .verifyComplete();
    }

    @Test
    void redeemCoupon_shouldThrowCouponNotFound_whenCodeDoesNotExist() {
        // Given
        when(couponRepository.findById("UNKNOWN")).thenReturn(Mono.empty());

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("UNKNOWN", "1.2.3.4", new RedeemCouponRequest(null)))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.COUPON_NOT_FOUND)
            .verify();
    }

    @Test
    void redeemCoupon_shouldThrowGeoUnavailable_whenGeoServiceFails() {
        // Given
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL));
        when(geoLocationService.getCountryCode(anyString())).thenReturn(Mono.error(new RuntimeException("timeout")));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest(null)))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.GEO_UNAVAILABLE)
            .verify();
    }

    @Test
    void redeemCoupon_shouldThrowCountryNotAllowed_whenCountryMismatch() {
        // Given
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL));
        when(geoLocationService.getCountryCode("1.2.3.4")).thenReturn(Mono.just("DE"));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest(null)))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.COUNTRY_NOT_ALLOWED)
            .verify();
    }

    @Test
    void redeemCoupon_shouldThrowCouponExhausted_whenAtomicUpdateMatchesNoRow() {
        // Given — empty Mono means cap reached (RETURNING yields no row)
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL));
        when(geoLocationService.getCountryCode("1.2.3.4")).thenReturn(Mono.just("PL"));
        when(couponRepository.atomicIncrementUsage("SPRING20")).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest(null)))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.COUPON_EXHAUSTED)
            .verify();
    }

    @Test
    void redeemCoupon_shouldThrowInvalidRequest_whenPerUserLimitTrueAndUserIdMissing() {
        // Given
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL_PER_USER));
        when(geoLocationService.getCountryCode("1.2.3.4")).thenReturn(Mono.just("PL"));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest(null)))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.INVALID_REQUEST)
            .verify();
    }

    @Test
    void redeemCoupon_shouldThrowAlreadyUsed_whenInsertViolatesUniqueConstraint() {
        // Given — pre-existence check removed; ALREADY_USED now comes from DB constraint violation
        when(couponRepository.findById("SPRING20")).thenReturn(Mono.just(COUPON_PL_PER_USER));
        when(geoLocationService.getCountryCode("1.2.3.4")).thenReturn(Mono.just("PL"));
        when(couponRepository.atomicIncrementUsage("SPRING20")).thenReturn(Mono.just(4));
        when(couponUsageRepository.save(any()))
            .thenReturn(Mono.error(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_coupon_usages_per_user\"")));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(inv -> inv.getArgument(0));

        // When / Then
        StepVerifier.create(couponService.redeemCoupon("SPRING20", "1.2.3.4", new RedeemCouponRequest("user1")))
            .expectErrorMatches(ex -> ex instanceof CouponException &&
                ((CouponException) ex).getErrorCode() == CouponErrorCode.ALREADY_USED)
            .verify();
    }
}

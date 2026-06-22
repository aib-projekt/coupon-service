package pl.aibprojekt.couponservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.aibprojekt.couponservice.application.GeoLocationService;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = CouponServiceApplication.class)
@Testcontainers
class CouponControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GeoLocationService geoLocationService;

    @BeforeEach
    void setUp() {
        when(geoLocationService.getCountryCode(anyString())).thenReturn(Mono.just("PL"));
    }

    // ====== POST /api/v1/coupons ======

    @Test
    void createCoupon_shouldReturn201_whenValidRequest() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"CREATE01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.code").isEqualTo("CREATE01")
            .jsonPath("$.currentUses").isEqualTo(0);
    }

    @Test
    void createCoupon_shouldReturn409_whenDuplicateCode() {
        String body = "{\"code\":\"DUP01\",\"maxUses\":5,\"country\":\"PL\",\"perUserLimit\":false}";
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.error").isEqualTo("COUPON_ALREADY_EXISTS");
    }

    @Test
    void createCoupon_shouldReturn400_whenMaxUsesIsZero() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"BAD01\",\"maxUses\":0,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.error").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void createCoupon_shouldReturn400_whenInvalidCountryCode() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"BAD02\",\"maxUses\":5,\"country\":\"XX\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.error").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void createCoupon_shouldNormalizeCodeToUppercase() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"lower01\",\"maxUses\":5,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody().jsonPath("$.code").isEqualTo("LOWER01");
    }

    // ====== GET /api/v1/coupons/{code} ======

    @Test
    void getCoupon_shouldReturn200_whenFound() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"GET01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.get().uri("/api/v1/coupons/GET01")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo("GET01")
            .jsonPath("$.maxUses").isEqualTo(10);
    }

    @Test
    void getCoupon_shouldReturn404_whenNotFound() {
        webTestClient.get().uri("/api/v1/coupons/NOTEXIST")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.error").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void getCoupon_shouldFindRegardlessOfInputCase() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"CASE01\",\"maxUses\":5,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.get().uri("/api/v1/coupons/case01")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.code").isEqualTo("CASE01");
    }

    // ====== POST /api/v1/coupons/{code}/redeem ======

    @Test
    void redeemCoupon_shouldReturn200_whenSuccess() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"REDEEM01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/REDEEM01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.remainingUses").isEqualTo(9);
    }

    @Test
    void redeemCoupon_shouldReturn404_whenCouponNotFound() {
        webTestClient.post().uri("/api/v1/coupons/NOEXIST/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.error").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void redeemCoupon_shouldReturn403_whenCountryMismatch() {
        when(geoLocationService.getCountryCode(anyString())).thenReturn(Mono.just("DE"));

        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"COUNTRY01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/COUNTRY01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody().jsonPath("$.error").isEqualTo("COUNTRY_NOT_ALLOWED");
    }

    @Test
    void redeemCoupon_shouldReturn503_whenGeoServiceUnavailable() {
        when(geoLocationService.getCountryCode(anyString()))
            .thenReturn(Mono.error(new CouponException(CouponErrorCode.GEO_UNAVAILABLE, "Geo service down")));

        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"GEO01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/GEO01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody().jsonPath("$.error").isEqualTo("GEO_UNAVAILABLE");
    }

    @Test
    void redeemCoupon_shouldReturn409Exhausted_whenMaxUsesReached() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"EXHAUST01\",\"maxUses\":1,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/EXHAUST01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange().expectStatus().isOk();

        webTestClient.post().uri("/api/v1/coupons/EXHAUST01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.error").isEqualTo("COUPON_EXHAUSTED");
    }

    @Test
    void redeemCoupon_shouldReturn409AlreadyUsed_whenPerUserLimitAndSecondRedemption() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"USER01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":true}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/USER01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":\"alice\"}")
            .exchange().expectStatus().isOk();

        webTestClient.post().uri("/api/v1/coupons/USER01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":\"alice\"}")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.error").isEqualTo("ALREADY_USED");
    }

    @Test
    void redeemCoupon_shouldAllowMultipleRedemptions_whenPerUserLimitFalse() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"MULTI01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        for (int i = 0; i < 3; i++) {
            webTestClient.post().uri("/api/v1/coupons/MULTI01/redeem")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "1.2.3.4")
                .bodyValue("{\"userId\":\"alice\"}")
                .exchange().expectStatus().isOk();
        }
    }

    @Test
    void redeemCoupon_shouldReturn400_whenUserIdMissingAndPerUserLimitTrue() {
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"USERID01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":true}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/USERID01/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.error").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void redeemCoupon_shouldReturn200_whenNoRequestBodyProvided() {
        // Tests CouponController null-body branch (request != null ? request : new RedeemCouponRequest(null))
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"NOBODY01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        webTestClient.post().uri("/api/v1/coupons/NOBODY01/redeem")
            .header("X-Forwarded-For", "1.2.3.4")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.remainingUses").isEqualTo(9);
    }

    @Test
    void createCoupon_shouldReturn400_whenCountryCodeIsNull() {
        // Tests CountryCodeValidator null branch
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"NULL01\",\"maxUses\":5,\"country\":null,\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.error").isEqualTo("INVALID_REQUEST");
    }

    // ====== Concurrency Tests ======

    @Test
    void concurrency_shouldNotExceedMaxUses_underConcurrentRedemptions() throws InterruptedException {
        // Given — coupon with maxUses=10
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"CONCURRENT01\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange().expectStatus().isCreated();

        int threads = 20;
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger exhausted = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var response = webTestClient.post().uri("/api/v1/coupons/CONCURRENT01/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .bodyValue("{\"userId\":null}")
                        .exchange()
                        .returnResult(String.class);
                    int status = response.getStatus().value();
                    if (status == 200) successes.incrementAndGet();
                    else if (status == 409) exhausted.incrementAndGet();
                } catch (Exception e) {
                    // count as failure
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown(); // release all threads simultaneously
        done.await();
        executor.shutdown();

        // Then — exactly 10 successes, never exceeded
        assertThat(successes.get()).isEqualTo(10);
        assertThat(exhausted.get()).isEqualTo(10);

        // Verify final state
        webTestClient.get().uri("/api/v1/coupons/CONCURRENT01")
            .exchange()
            .expectBody()
            .jsonPath("$.currentUses").isEqualTo(10);
    }

    @Test
    void concurrency_shouldReturn1SuccessOnly_forSameUserConcurrentRedemptions() throws InterruptedException {
        // Given — per-user coupon
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"PERUSER01\",\"maxUses\":100,\"country\":\"PL\",\"perUserLimit\":true}")
            .exchange().expectStatus().isCreated();

        int threads = 10;
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successes = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var response = webTestClient.post().uri("/api/v1/coupons/PERUSER01/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .bodyValue("{\"userId\":\"sameuser\"}")
                        .exchange()
                        .returnResult(String.class);
                    if (response.getStatus().value() == 200) successes.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await();
        executor.shutdown();

        // Then — exactly 1 success
        assertThat(successes.get()).isEqualTo(1);
    }
}

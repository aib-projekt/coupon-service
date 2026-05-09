package pl.aibprojekt.couponservice.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import pl.aibprojekt.couponservice.CouponServiceApplication;
import pl.aibprojekt.couponservice.api.dto.CouponResponse;
import pl.aibprojekt.couponservice.api.dto.RedemptionResponse;
import pl.aibprojekt.couponservice.api.validation.CountryCodeValidator;
import pl.aibprojekt.couponservice.application.CouponService;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;
import pl.aibprojekt.couponservice.domain.CouponException;
import pl.aibprojekt.couponservice.infrastructure.IpAddressExtractor;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = CouponController.class)
@ContextConfiguration(classes = CouponServiceApplication.class)
@Import(CouponControllerTest.TestConfig.class)
class CouponControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CouponService couponService;

    @Test
    void createCoupon_shouldReturn201_whenValidRequest() {
        // Given
        CouponResponse response = new CouponResponse("SPRING20", 10, 0, "PL", false, Instant.now());
        when(couponService.createCoupon(any())).thenReturn(Mono.just(response));

        // When / Then
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"SPRING20\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.code").isEqualTo("SPRING20");
    }

    @Test
    void createCoupon_shouldReturn409_whenDuplicateCode() {
        // Given
        when(couponService.createCoupon(any()))
            .thenReturn(Mono.error(new CouponException(CouponErrorCode.COUPON_ALREADY_EXISTS, "Coupon already exists.")));

        // When / Then
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"SPRING20\",\"maxUses\":10,\"country\":\"PL\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.error").isEqualTo("COUPON_ALREADY_EXISTS");
    }

    @Test
    void createCoupon_shouldReturn400_whenInvalidCountryCode() {
        // When / Then
        webTestClient.post().uri("/api/v1/coupons")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"code\":\"SPRING20\",\"maxUses\":10,\"country\":\"XX\",\"perUserLimit\":false}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void getCoupon_shouldReturn200_whenFound() {
        // Given
        CouponResponse response = new CouponResponse("SPRING20", 10, 2, "PL", false, Instant.now());
        when(couponService.getCoupon("SPRING20")).thenReturn(Mono.just(response));

        // When / Then
        webTestClient.get().uri("/api/v1/coupons/spring20")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo("SPRING20")
            .jsonPath("$.currentUses").isEqualTo(2);
    }

    @Test
    void getCoupon_shouldReturn404_whenNotFound() {
        // Given
        when(couponService.getCoupon("UNKNOWN"))
            .thenReturn(Mono.error(new CouponException(CouponErrorCode.COUPON_NOT_FOUND, "Coupon not found.")));

        // When / Then
        webTestClient.get().uri("/api/v1/coupons/UNKNOWN")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.error").isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void redeemCoupon_shouldReturn200_whenSuccess() {
        // Given
        RedemptionResponse response = new RedemptionResponse("SPRING20", 9, Instant.now());
        when(couponService.redeemCoupon(eq("SPRING20"), anyString(), any()))
            .thenReturn(Mono.just(response));

        // When / Then
        webTestClient.post().uri("/api/v1/coupons/SPRING20/redeem")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "1.2.3.4")
            .bodyValue("{\"userId\":null}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.remainingUses").isEqualTo(9);
    }

    @Configuration
    static class TestConfig {
        @Bean
        CouponController couponController(CouponService couponService, IpAddressExtractor ipAddressExtractor) {
            return new CouponController(couponService, ipAddressExtractor);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        CountryCodeValidator countryCodeValidator() {
            return new CountryCodeValidator();
        }

        @Bean
        IpAddressExtractor ipAddressExtractor() {
            return new IpAddressExtractor(
                new pl.aibprojekt.couponservice.infrastructure.config.GeoProperties(
                    "http://ip-api.com", 2, 3, 1));
        }
    }
}

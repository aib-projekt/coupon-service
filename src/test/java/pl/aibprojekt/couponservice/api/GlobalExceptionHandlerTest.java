package pl.aibprojekt.couponservice.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import pl.aibprojekt.couponservice.domain.CouponErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnAlreadyUsed_whenConstraintContainsUqCouponUsagesPerUser() {
        // Given
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint \"uq_coupon_usages_per_user\"");

        // When
        var response = handler.handleDataIntegrityViolation(ex);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(CouponErrorCode.ALREADY_USED.getHttpStatus().value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(CouponErrorCode.ALREADY_USED.name());
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnCouponAlreadyExists_whenConstraintIsOther() {
        // Given
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint \"coupons_pkey\"");

        // When
        var response = handler.handleDataIntegrityViolation(ex);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(CouponErrorCode.COUPON_ALREADY_EXISTS.getHttpStatus().value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(CouponErrorCode.COUPON_ALREADY_EXISTS.name());
    }

    @Test
    void handleDataIntegrityViolation_shouldReturnCouponAlreadyExists_whenMessageIsNull() {
        // Given — covers the message != null → false branch
        DataIntegrityViolationException ex = new DataIntegrityViolationException((String) null);

        // When
        var response = handler.handleDataIntegrityViolation(ex);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(CouponErrorCode.COUPON_ALREADY_EXISTS.getHttpStatus().value());
    }

    @Test
    void handleGenericException_shouldReturnInternalServerError() {
        // Given
        RuntimeException ex = new RuntimeException("Unexpected failure");

        // When
        var response = handler.handleGenericException(ex);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
    }
}

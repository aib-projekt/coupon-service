package pl.aibprojekt.couponservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class CouponErrorCodeTest {

    @Test
    void allErrorCodes_shouldMapToCorrectHttpStatus() {
        // Given / When / Then
        assertThat(CouponErrorCode.COUPON_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(CouponErrorCode.COUPON_EXHAUSTED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CouponErrorCode.COUNTRY_NOT_ALLOWED.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(CouponErrorCode.ALREADY_USED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CouponErrorCode.GEO_UNAVAILABLE.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(CouponErrorCode.COUPON_ALREADY_EXISTS.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(CouponErrorCode.INVALID_REQUEST.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

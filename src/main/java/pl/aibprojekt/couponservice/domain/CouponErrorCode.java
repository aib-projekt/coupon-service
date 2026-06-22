package pl.aibprojekt.couponservice.domain;

import org.springframework.http.HttpStatus;

public enum CouponErrorCode {
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT),
    COUNTRY_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    ALREADY_USED(HttpStatus.CONFLICT),
    GEO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    COUPON_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus httpStatus;

    CouponErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

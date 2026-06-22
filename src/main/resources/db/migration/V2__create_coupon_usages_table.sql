CREATE TABLE coupon_usages (
    id           BIGSERIAL    PRIMARY KEY,
    coupon_code  VARCHAR(64)  NOT NULL REFERENCES coupons(code),
    user_id      VARCHAR(255) NOT NULL,
    used_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_usages_per_user UNIQUE (coupon_code, user_id)
);

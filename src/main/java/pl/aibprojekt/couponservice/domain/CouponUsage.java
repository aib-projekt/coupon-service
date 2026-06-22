package pl.aibprojekt.couponservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("coupon_usages")
public record CouponUsage(
    @Id Long id,
    @Column("coupon_code") String couponCode,
    @Column("user_id") String userId,
    @Column("used_at") Instant usedAt
) {}

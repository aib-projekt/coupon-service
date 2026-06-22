package pl.aibprojekt.couponservice.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("coupons")
public record Coupon(
    @Id String code,
    @Column("max_uses") int maxUses,
    @Column("current_uses") int currentUses,
    @Column("country") String country,
    @Column("per_user_limit") boolean perUserLimit,
    @Column("created_at") Instant createdAt
) {}

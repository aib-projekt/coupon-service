CREATE TABLE coupons (
    code           VARCHAR(64)  PRIMARY KEY,
    max_uses       INTEGER      NOT NULL CHECK (max_uses >= 1),
    current_uses   INTEGER      NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    country        CHAR(2)      NOT NULL,
    per_user_limit BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

package org.springframework.boot.actuate.health;

/**
 * Binary-compatible stand-in for Spring Boot 3's {@code Health}.
 *
 * <p>The builder methods yaci-store 2.0.7 calls ({@code unknown}, {@code up}, {@code down},
 * {@code outOfService}, {@code withDetail}, {@code build}) delegate to Boot 4's
 * {@code org.springframework.boot.health.contributor.Health}. {@link #unwrap()} exposes that
 * instance so a Boot 4 health contributor can publish it.
 */
public final class Health {

    private final org.springframework.boot.health.contributor.Health delegate;

    private Health(org.springframework.boot.health.contributor.Health delegate) {
        this.delegate = delegate;
    }

    public static Builder unknown() {
        return new Builder(org.springframework.boot.health.contributor.Health.unknown());
    }

    public org.springframework.boot.health.contributor.Health unwrap() {
        return delegate;
    }

    public static final class Builder {

        private final org.springframework.boot.health.contributor.Health.Builder delegate;

        public Builder() {
            this(new org.springframework.boot.health.contributor.Health.Builder());
        }

        private Builder(org.springframework.boot.health.contributor.Health.Builder delegate) {
            this.delegate = delegate;
        }

        public Builder withDetail(String key, Object value) {
            this.delegate.withDetail(key, value);
            return this;
        }

        public Builder up() {
            this.delegate.up();
            return this;
        }

        public Builder down() {
            this.delegate.down();
            return this;
        }

        public Builder outOfService() {
            this.delegate.outOfService();
            return this;
        }

        public Health build() {
            return new Health(this.delegate.build());
        }
    }
}

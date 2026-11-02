package org.springframework.boot.actuate.health;

/**
 * Binary-compatible stand-in for Spring Boot 3's {@code HealthIndicator}.
 *
 * <p>Boot 4 moved the type to {@code org.springframework.boot.health.contributor.HealthIndicator}.
 * yaci-store 2.0.7's node health components still implement this package. Boot 4 does not treat
 * this interface as a health contributor; {@code YaciStoreSpringBoot4Bridge} adapts the beans.
 */
public interface HealthIndicator {

    Health health();
}

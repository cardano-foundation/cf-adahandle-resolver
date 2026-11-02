package org.springframework.boot.autoconfigure.jooq;

import org.jooq.impl.DefaultConfiguration;

/**
 * Binary-compatible stand-in for Spring Boot 3's jOOQ {@code DefaultConfigurationCustomizer}.
 *
 * <p>yaci-store 2.0.7's {@code YaciStoreAutoConfiguration#configurationCustomiser()} returns this
 * type and implements it with a lambda. Boot 4 moved the interface to
 * {@code org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer}, so the
 * method cannot be linked unless this type exists. Boot 4's jOOQ auto-configuration only calls
 * the new interface. {@code YaciStoreSpringBoot4Bridge} registers an equivalent customizer there
 * (render unquoted identifiers, which is what the yaci-store lambda does).
 */
@FunctionalInterface
public interface DefaultConfigurationCustomizer {

    void customize(DefaultConfiguration configuration);
}

package org.cardanofoundation.tools.adahandle.resolver.config;

import com.bloxbean.cardano.yaci.store.core.health.NodeHealthIndicator;
import com.bloxbean.cardano.yaci.store.core.health.NodeSyncIndicator;
import org.jooq.conf.RenderQuotedNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adapts yaci-store 2.0.7, which is compiled against Spring Boot 3.3, to Spring Boot 4.
 *
 * <p>There is no stable yaci-store release built for Spring Boot 4. The 2.0.7 starters (and the
 * 3.0.0 pre-releases) import {@code spring-boot-dependencies} 3.3.4. Two Boot 3 types those
 * classes reference were removed in Boot 4; the stand-ins live in the original packages so the
 * yaci-store classes still link. The behaviour those types used to provide is re-registered here
 * against the Boot 4 APIs:
 *
 * <ul>
 *   <li>{@code @EntityScan} on yaci-store's configuration classes names
 *       {@code org.springframework.boot.autoconfigure.domain.EntityScan}, which Boot 4 no longer
 *       processes. The same packages are scanned with Boot 4's annotation.</li>
 *   <li>yaci-store's jOOQ customizer implements the Boot 3 interface, which Boot 4 does not call.
 *       It turns identifier quoting off so generated SQL matches the unquoted names in the
 *       Flyway scripts. The same setting is applied through Boot 4's customizer.</li>
 *   <li>{@code NodeHealthIndicator} and {@code NodeSyncIndicator} implement Boot 3's
 *       {@code org.springframework.boot.actuate.health.HealthIndicator}. Boot 4 only contributes
 *       beans of {@code org.springframework.boot.health.contributor.HealthIndicator}, so the
 *       results are adapted under the same contributor names.</li>
 * </ul>
 */
@Configuration
@EntityScan(basePackages = {
        "com.bloxbean.cardano.yaci.store.common",
        "com.bloxbean.cardano.yaci.store.events",
        "com.bloxbean.cardano.yaci.store.core",
        "com.bloxbean.cardano.yaci.store.utxo"
})
public class YaciStoreSpringBoot4Bridge {

    @Bean
    public DefaultConfigurationCustomizer yaciStoreJooqIdentifierQuoting() {
        return configuration -> configuration.settings().withRenderQuotedNames(RenderQuotedNames.NEVER);
    }

    // The "HealthIndicator" suffix is stripped, so these contribute as "nodeHealth" and
    // "nodeSync", the names yaci-store's components use. Those components stay on the Boot 3
    // interface and are not contributors themselves.
    @Bean
    @ConditionalOnBean(NodeHealthIndicator.class)
    public HealthIndicator nodeHealthHealthIndicator(NodeHealthIndicator indicator) {
        return () -> indicator.health().unwrap();
    }

    @Bean
    @ConditionalOnBean(NodeSyncIndicator.class)
    public HealthIndicator nodeSyncHealthIndicator(NodeSyncIndicator indicator) {
        return () -> indicator.health().unwrap();
    }
}

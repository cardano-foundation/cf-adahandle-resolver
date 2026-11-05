package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * {@code adahandle_handles{type}}: the handles that exist now, as the API resolves them, read from the database. API-only
 * replicas ({@code disable-indexer}) report the shared database's count too.
 * <p>
 * Counting takes a scan of ada_handle (tens of milliseconds on mainnet), so the counts are kept for
 * {@link #MAX_AGE} rather than read on every scrape.
 */
@Component
public class AdaHandleCountMetrics {

    static final Duration MAX_AGE = Duration.ofSeconds(30);

    private final AdaHandleRepository repository;
    private final Clock clock;
    private long readAt = Long.MIN_VALUE;
    private long handles;
    private long subHandles;

    @Autowired
    public AdaHandleCountMetrics(AdaHandleRepository repository, MeterRegistry registry) {
        this(repository, registry, Clock.systemUTC());
    }

    AdaHandleCountMetrics(AdaHandleRepository repository, MeterRegistry registry, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        Gauge.builder("adahandle.handles", this, metrics -> metrics.counts()[0])
                .description("Handles that exist now (not burned), as the API resolves them")
                .tag("type", AdaHandleMetrics.HANDLE)
                .register(registry);
        Gauge.builder("adahandle.handles", this, metrics -> metrics.counts()[1])
                .description("Handles that exist now (not burned), as the API resolves them")
                .tag("type", AdaHandleMetrics.SUBHANDLE)
                .register(registry);
    }

    /** {handles, sub-handles}, at most {@link #MAX_AGE} old. */
    synchronized long[] counts() {
        long now = clock.millis();
        if (readAt == Long.MIN_VALUE || now - readAt >= MAX_AGE.toMillis()) {
            long all = repository.count();
            subHandles = repository.countSubHandles();
            handles = all - subHandles;
            readAt = now;
        }
        return new long[]{handles, subHandles};
    }
}

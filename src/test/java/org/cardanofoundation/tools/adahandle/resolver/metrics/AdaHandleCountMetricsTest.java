package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaHandleCountMetricsTest {

    private final AdaHandleRepository repository = mock(AdaHandleRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-04T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void reportsHandlesAndSubHandlesAndReadsAtMostEvery30Seconds() {
        when(repository.count()).thenReturn(100L, 110L);
        when(repository.countSubHandles()).thenReturn(30L, 31L);
        new AdaHandleCountMetrics(repository, registry, clock);

        assertThat(gauge("handle"), is(70.0));
        assertThat(gauge("subhandle"), is(30.0));

        now.set(now.get().plus(Duration.ofSeconds(29)));
        assertThat(gauge("handle"), is(70.0));
        verify(repository, times(1)).count();

        now.set(now.get().plus(Duration.ofSeconds(1)));
        assertThat(gauge("handle"), is(79.0));
        assertThat(gauge("subhandle"), is(31.0));
        verify(repository, times(2)).count();
    }

    private double gauge(String type) {
        return registry.get("adahandle.handles").tag("type", type).gauge().value();
    }
}

package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryMetricsTest {

    private static final long MB = 1024 * 1024;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong total = new AtomicLong(300 * MB);
    private final AtomicLong free = new AtomicLong(100 * MB);
    private final MemoryMetrics.Heap heap = new MemoryMetrics.Heap() {
        @Override
        public long total() {
            return total.get();
        }

        @Override
        public long free() {
            return free.get();
        }

        @Override
        public long max() {
            return 2048 * MB;
        }
    };

    @TempDir
    Path dir;

    @Test
    void withoutHeapPoolsTheHeapIsReportedAsAWhole() {
        // What the native image with G1 reports: only the code cache pools.
        new MemoryMetrics(registry, List.of(pool(MemoryType.NON_HEAP)), heap, dir.resolve("missing"));

        assertThat(heapGauge("jvm.memory.used").value()).isEqualTo(200.0 * MB);
        assertThat(heapGauge("jvm.memory.committed").value()).isEqualTo(300.0 * MB);
        assertThat(heapGauge("jvm.memory.max").value()).isEqualTo(2048.0 * MB);
    }

    @Test
    void heapGaugesFollowTheHeap() {
        new MemoryMetrics(registry, List.of(), heap, dir.resolve("missing"));

        total.set(500 * MB);
        free.set(50 * MB);

        assertThat(heapGauge("jvm.memory.used").value()).isEqualTo(450.0 * MB);
        assertThat(heapGauge("jvm.memory.committed").value()).isEqualTo(500.0 * MB);
    }

    @Test
    void onTheJvmMicrometersPoolMetricsAreLeftAlone() {
        new MemoryMetrics(registry, List.of(pool(MemoryType.HEAP), pool(MemoryType.NON_HEAP)), heap,
                dir.resolve("missing"));

        assertThat(registry.find("jvm.memory.used").tag("id", MemoryMetrics.HEAP_ID).gauge()).isNull();
        assertThat(registry.find("jvm.memory.committed").gauge()).isNull();
        assertThat(registry.find("jvm.memory.max").gauge()).isNull();
    }

    @Test
    void residentMemoryComesFromProcStatus() throws IOException {
        Path status = Files.writeString(dir.resolve("status"), """
                Name:\tadahandle-resol
                VmPeak:\t 1200000 kB
                VmHWM:\t  600000 kB
                VmRSS:\t  488988 kB
                Threads:\t42
                """);

        new MemoryMetrics(registry, List.of(pool(MemoryType.HEAP)), heap, status);

        Gauge rss = registry.find("process.memory.rss").gauge();
        assertThat(rss).isNotNull();
        assertThat(rss.value()).isEqualTo(488988.0 * 1024);
        assertThat(rss.getId().getBaseUnit()).isEqualTo("bytes");
    }

    @Test
    void residentMemoryIsReadAtEveryScrape() throws IOException {
        Path status = Files.writeString(dir.resolve("status"), "VmRSS:\t 1000 kB\n");
        new MemoryMetrics(registry, List.of(pool(MemoryType.HEAP)), heap, status);

        Files.writeString(status, "VmRSS:\t 2000 kB\n");

        assertThat(registry.find("process.memory.rss").gauge().value()).isEqualTo(2000.0 * 1024);
    }

    @Test
    void noProcStatusNoResidentMemoryGauge() {
        // macOS, Windows.
        new MemoryMetrics(registry, List.of(pool(MemoryType.HEAP)), heap, dir.resolve("missing"));

        assertThat(registry.find("process.memory.rss").gauge()).isNull();
    }

    @Test
    void unreadableVmRssNoResidentMemoryGauge() throws IOException {
        Path status = Files.writeString(dir.resolve("status"), "Name:\tsomething\nVmRSS:\tgarbage kB\n");

        new MemoryMetrics(registry, List.of(pool(MemoryType.HEAP)), heap, status);

        assertThat(registry.find("process.memory.rss").gauge()).isNull();
    }

    private Gauge heapGauge(String name) {
        Gauge gauge = registry.find(name).tag("area", "heap").tag("id", MemoryMetrics.HEAP_ID).gauge();
        assertThat(gauge).as(name).isNotNull();
        return gauge;
    }

    private static MemoryPoolMXBean pool(MemoryType type) {
        MemoryPoolMXBean pool = mock(MemoryPoolMXBean.class);
        when(pool.getType()).thenReturn(type);
        return pool;
    }
}

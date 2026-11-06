package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

/**
 * Memory metrics that also work in the GraalVM native image.
 * <p>
 * Micrometer's {@code jvm_memory_*} metrics come from the JVM's memory pools (eden, survivor, old gen). The native
 * image built with G1 reports none for the heap, only two code cache pools that read 0, so Prometheus showed no heap
 * at all. When no heap pool exists, this adds {@code jvm_memory_used_bytes}, {@code jvm_memory_committed_bytes} and
 * {@code jvm_memory_max_bytes} with {@code area="heap", id="heap"}, from {@link Runtime}, which the native image
 * supports. On the JVM, Micrometer's per-pool metrics stay as they are and nothing is added.
 * <p>
 * {@code process_memory_rss_bytes}: the process's resident memory, from {@code /proc/self/status} (Linux only), for both
 * images. It includes the native binary's mapped pages, which the kernel can reclaim, so it reads higher than a
 * container's memory usage.
 */
@Slf4j
@Component
public class MemoryMetrics {

    static final String HEAP_ID = "heap";

    /** The heap as {@link Runtime} reports it. */
    interface Heap {
        long total();

        long free();

        long max();
    }

    private static final Heap RUNTIME_HEAP = new Heap() {
        @Override
        public long total() {
            return Runtime.getRuntime().totalMemory();
        }

        @Override
        public long free() {
            return Runtime.getRuntime().freeMemory();
        }

        @Override
        public long max() {
            return Runtime.getRuntime().maxMemory();
        }
    };

    private final Path procStatus;

    @Autowired
    public MemoryMetrics(MeterRegistry registry) {
        this(registry, ManagementFactory.getMemoryPoolMXBeans(), RUNTIME_HEAP, Path.of("/proc/self/status"));
    }

    MemoryMetrics(MeterRegistry registry, List<MemoryPoolMXBean> pools, Heap heap, Path procStatus) {
        this.procStatus = procStatus;
        boolean heapPools = pools.stream().anyMatch(pool -> pool.getType() == MemoryType.HEAP);
        if (!heapPools) {
            log.info("No heap memory pools (GraalVM native image): reporting the heap as a whole");
            heapGauge(registry, "jvm.memory.used", "The amount of used memory", heap,
                    h -> h.total() - h.free());
            heapGauge(registry, "jvm.memory.committed",
                    "The amount of memory in bytes that is committed for the Java virtual machine to use", heap,
                    Heap::total);
            heapGauge(registry, "jvm.memory.max",
                    "The maximum amount of memory in bytes that can be used for memory management", heap, Heap::max);
        }
        if (residentBytes().isPresent()) {
            Gauge.builder("process.memory.rss", this, metrics -> metrics.residentBytes().orElse(-1))
                    .description("Resident memory of the process (VmRSS), including mapped pages of the executable")
                    .baseUnit("bytes")
                    .register(registry);
        }
    }

    private static void heapGauge(MeterRegistry registry, String name, String description, Heap heap,
                                  java.util.function.ToDoubleFunction<Heap> value) {
        Gauge.builder(name, heap, value)
                .description(description)
                .tag("area", "heap")
                .tag("id", HEAP_ID)
                .baseUnit("bytes")
                .register(registry);
    }

    /** VmRSS from /proc/self/status, in bytes; empty where there is no such file (not Linux). */
    OptionalLong residentBytes() {
        if (!Files.isReadable(procStatus)) {
            return OptionalLong.empty();
        }
        try {
            for (String line : Files.readAllLines(procStatus)) {
                if (line.startsWith("VmRSS:")) {
                    // "VmRSS:     488988 kB"
                    String[] parts = line.substring("VmRSS:".length()).trim().split("\\s+");
                    return OptionalLong.of(Long.parseLong(parts[0]) * 1024);
                }
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read {}: {}", procStatus, e.getMessage());
        }
        return OptionalLong.empty();
    }
}

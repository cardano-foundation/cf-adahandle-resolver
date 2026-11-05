package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Counters of what the indexer writes, in Prometheus as {@code adahandle_*_total}. They count from the start of the
 * process: during the initial sync they count every block from the first ADA Handle mint. Changes of blocks rolled back
 * before they were committed are not counted.
 * <p>
 * {@code type} is {@code handle} or {@code subhandle} (a name with {@code @}).
 */
@Component
public class AdaHandleMetrics {

    static final String HANDLE = "handle";
    static final String SUBHANDLE = "subhandle";

    private final Counter handleMints;
    private final Counter subHandleMints;
    private final Counter handleBurns;
    private final Counter subHandleBurns;
    private final Counter historyRows;
    private final Counter noopRollbacks;
    private final Counter appliedRollbacks;
    private final Counter rolledBackHandles;

    public AdaHandleMetrics(MeterRegistry registry) {
        handleMints = mints(registry, HANDLE);
        subHandleMints = mints(registry, SUBHANDLE);
        handleBurns = burns(registry, HANDLE);
        subHandleBurns = burns(registry, SUBHANDLE);
        historyRows = Counter.builder("adahandle.history.rows")
                .description("Handle states written to the history: one per handle and block it changed in")
                .register(registry);
        noopRollbacks = rollbacks(registry, "noop");
        appliedRollbacks = rollbacks(registry, "applied");
        rolledBackHandles = Counter.builder("adahandle.rollback.handles")
                .description("Handles restored to their previous owner, or removed, by rollbacks")
                .register(registry);
    }

    private static Counter mints(MeterRegistry registry, String type) {
        return Counter.builder("adahandle.mints")
                .description("Handle tokens minted (a legacy handle moved to CIP-68 counts as a mint and a burn)")
                .tag("type", type)
                .register(registry);
    }

    private static Counter burns(MeterRegistry registry, String type) {
        return Counter.builder("adahandle.burns")
                .description("Handle tokens burned")
                .tag("type", type)
                .register(registry);
    }

    private static Counter rollbacks(MeterRegistry registry, String outcome) {
        return Counter.builder("adahandle.rollbacks")
                .description("Rollbacks handled; noop: nothing stored after the rollback point")
                .tag("outcome", outcome)
                .register(registry);
    }

    static String type(String name) {
        return name.contains("@") ? SUBHANDLE : HANDLE;
    }

    public void minted(String name) {
        (type(name).equals(SUBHANDLE) ? subHandleMints : handleMints).increment();
    }

    public void burned(String name) {
        (type(name).equals(SUBHANDLE) ? subHandleBurns : handleBurns).increment();
    }

    public void historyRowsWritten(int rows) {
        historyRows.increment(rows);
    }

    /** @param affectedHandles handles restored or removed; 0 when the rollback removed nothing */
    public void rolledBack(int affectedHandles) {
        if (affectedHandles == 0) {
            noopRollbacks.increment();
        } else {
            appliedRollbacks.increment();
            rolledBackHandles.increment(affectedHandles);
        }
    }
}

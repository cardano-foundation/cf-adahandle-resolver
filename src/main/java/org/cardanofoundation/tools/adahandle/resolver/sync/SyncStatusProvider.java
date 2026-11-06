package org.cardanofoundation.tools.adahandle.resolver.sync;

import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.store.common.domain.HealthStatus;
import com.bloxbean.cardano.yaci.store.common.domain.SyncStatus;
import com.bloxbean.cardano.yaci.store.common.service.CursorService;
import com.bloxbean.cardano.yaci.store.core.service.EraService;
import com.bloxbean.cardano.yaci.store.core.service.HealthService;
import com.bloxbean.cardano.yaci.store.core.service.SyncStatusService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Keeps a snapshot of the sync status, built from Yaci Store's cursor, network tip and block-receive health. It is
 * refreshed in the background, every few seconds, so an API request never waits for the database or the node: Yaci
 * Store fetches the network tip from the node, which can take seconds. See ADR 12.
 */
@Slf4j
@Component
public class SyncStatusProvider {

    private final ObjectProvider<SyncStatusService> syncStatusService;
    private final ObjectProvider<HealthService> healthService;
    private final ObjectProvider<CursorService> cursorService;
    private final ObjectProvider<EraService> eraService;

    private volatile SyncStatusSnapshot snapshot = SyncStatusSnapshot.UNKNOWN;

    public SyncStatusProvider(ObjectProvider<SyncStatusService> syncStatusService,
                              ObjectProvider<HealthService> healthService,
                              ObjectProvider<CursorService> cursorService,
                              ObjectProvider<EraService> eraService) {
        this.syncStatusService = syncStatusService;
        this.healthService = healthService;
        this.cursorService = cursorService;
        this.eraService = eraService;
    }

    public SyncStatusSnapshot current() {
        return snapshot;
    }

    @Scheduled(initialDelayString = "${adahandle.sync-status.refresh-ms:5000}",
            fixedDelayString = "${adahandle.sync-status.refresh-ms:5000}")
    public void refresh() {
        try {
            snapshot = read();
        } catch (RuntimeException e) {
            log.debug("Could not read the sync status: {}", e.getMessage());
            snapshot = SyncStatusSnapshot.UNKNOWN;
        }
    }

    private SyncStatusSnapshot read() {
        CursorService cursors = cursorService.getIfAvailable();
        SyncStatusService syncStatuses = syncStatusService.getIfAvailable();
        HealthService health = healthService.getIfAvailable();
        if (cursors == null || syncStatuses == null || health == null || cursors.getCursor().isEmpty()) {
            return SyncStatusSnapshot.UNKNOWN;
        }
        Era era = cursors.getCursor().get().getEra();
        SyncStatus sync = syncStatuses.getSyncStatus();
        return snapshot(sync, health.getHealthStatus(), blockTime(era, sync.slot()));
    }

    private @Nullable Instant blockTime(@Nullable Era era, long slot) {
        EraService eras = eraService.getIfAvailable();
        if (eras == null || era == null) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(eras.blockTime(era, slot));
        } catch (RuntimeException e) {
            log.debug("Could not compute the time of slot {}: {}", slot, e.getMessage());
            return null;
        }
    }

    /**
     * The decision itself. Not receiving blocks wins over everything else: a resolver cut off from the node may have
     * been at the tip once, but its answers age from then on.
     */
    static SyncStatusSnapshot snapshot(@Nullable SyncStatus sync, @Nullable HealthStatus health,
                                       @Nullable Instant blockTime) {
        if (sync == null || sync.slot() <= 0) {
            return SyncStatusSnapshot.UNKNOWN;
        }
        Long tipSlot = sync.networkTipAvailable() ? sync.networkSlot() : null;
        SyncState state;
        if (health == null || !health.isReceivingBlocks() || health.isScheduleToStop() || health.isError()) {
            state = SyncState.STALE;
        } else if (tipSlot == null) {
            state = SyncState.UNKNOWN;
        } else if (sync.synced()) {
            state = SyncState.SYNCED;
        } else {
            state = SyncState.SYNCING;
        }
        return new SyncStatusSnapshot(state, sync.slot(), sync.block(), blockTime, tipSlot);
    }
}

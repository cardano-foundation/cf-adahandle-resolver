package org.cardanofoundation.tools.adahandle.resolver.sync;

import com.bloxbean.cardano.yaci.core.model.Era;
import com.bloxbean.cardano.yaci.store.common.domain.Cursor;
import com.bloxbean.cardano.yaci.store.common.domain.HealthStatus;
import com.bloxbean.cardano.yaci.store.common.domain.SyncStatus;
import com.bloxbean.cardano.yaci.store.common.service.CursorService;
import com.bloxbean.cardano.yaci.store.core.service.EraService;
import com.bloxbean.cardano.yaci.store.core.service.HealthService;
import com.bloxbean.cardano.yaci.store.core.service.SyncStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncStatusProviderTest {

    private static final Instant BLOCK_TIME = Instant.parse("2026-11-06T10:00:00Z");

    private final SyncStatusService syncStatusService = mock(SyncStatusService.class);
    private final HealthService healthService = mock(HealthService.class);
    private final CursorService cursorService = mock(CursorService.class);
    private final EraService eraService = mock(EraService.class);
    private SyncStatusProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SyncStatusProvider(providerOf(syncStatusService), providerOf(healthService),
                providerOf(cursorService), providerOf(eraService));
    }

    // The decision

    @Test
    void atTheTipAndReceivingBlocksIsSynced() {
        SyncStatusSnapshot snapshot = SyncStatusProvider.snapshot(sync(1000, 1002, true), receiving(), BLOCK_TIME);

        assertThat(snapshot).isEqualTo(new SyncStatusSnapshot(SyncState.SYNCED, 1000L, 10L, BLOCK_TIME, 1002L));
    }

    @Test
    void behindTheTipIsSyncing() {
        SyncStatusSnapshot snapshot = SyncStatusProvider.snapshot(sync(1000, 900_000, false), receiving(), BLOCK_TIME);

        assertThat(snapshot.state()).isEqualTo(SyncState.SYNCING);
        assertThat(snapshot.tipSlot()).isEqualTo(900_000L);
    }

    @Test
    void noBlocksFromTheNodeIsStaleEvenAtTheTip() {
        SyncStatusSnapshot snapshot = SyncStatusProvider.snapshot(sync(1000, 1000, true), notReceiving(), BLOCK_TIME);

        assertThat(snapshot.state()).isEqualTo(SyncState.STALE);
        assertThat(snapshot.slot()).isEqualTo(1000L);
    }

    @Test
    void aFetcherScheduledToStopIsStale() {
        HealthStatus health = HealthStatus.builder().isReceivingBlocks(true).isScheduleToStop(true).build();

        assertThat(SyncStatusProvider.snapshot(sync(1000, 1000, true), health, BLOCK_TIME).state())
                .isEqualTo(SyncState.STALE);
    }

    @Test
    void aFetcherInErrorIsStale() {
        HealthStatus health = HealthStatus.builder().isReceivingBlocks(true).isError(true).build();

        assertThat(SyncStatusProvider.snapshot(sync(1000, 1000, true), health, BLOCK_TIME).state())
                .isEqualTo(SyncState.STALE);
    }

    @Test
    void unknownNetworkTipIsUnknownButKeepsTheCursor() {
        SyncStatus sync = sync(1000, SyncStatus.UNKNOWN_NETWORK_TIP, false);

        SyncStatusSnapshot snapshot = SyncStatusProvider.snapshot(sync, receiving(), BLOCK_TIME);

        assertThat(snapshot).isEqualTo(new SyncStatusSnapshot(SyncState.UNKNOWN, 1000L, 10L, BLOCK_TIME, null));
    }

    @Test
    void nothingIndexedIsUnknown() {
        assertThat(SyncStatusProvider.snapshot(sync(0, 1000, false), receiving(), null))
                .isEqualTo(SyncStatusSnapshot.UNKNOWN);
    }

    // Reading Yaci Store's services

    @Test
    void startsUnknown() {
        assertThat(provider.current()).isEqualTo(SyncStatusSnapshot.UNKNOWN);
    }

    @Test
    void refreshReadsCursorTipHealthAndBlockTime() {
        when(cursorService.getCursor()).thenReturn(Optional.of(Cursor.builder().slot(1000L).block(10L)
                .era(Era.Conway).build()));
        when(syncStatusService.getSyncStatus()).thenReturn(sync(1000, 1002, true));
        when(healthService.getHealthStatus()).thenReturn(receiving());
        when(eraService.blockTime(Era.Conway, 1000L)).thenReturn(BLOCK_TIME.getEpochSecond());

        provider.refresh();

        assertThat(provider.current())
                .isEqualTo(new SyncStatusSnapshot(SyncState.SYNCED, 1000L, 10L, BLOCK_TIME, 1002L));
    }

    @Test
    void refreshWithoutACursorIsUnknown() {
        when(cursorService.getCursor()).thenReturn(Optional.empty());

        provider.refresh();

        assertThat(provider.current()).isEqualTo(SyncStatusSnapshot.UNKNOWN);
    }

    @Test
    void aFailingBlockTimeLeavesItOut() {
        when(cursorService.getCursor()).thenReturn(Optional.of(Cursor.builder().slot(1000L).block(10L)
                .era(Era.Conway).build()));
        when(syncStatusService.getSyncStatus()).thenReturn(sync(1000, 1002, true));
        when(healthService.getHealthStatus()).thenReturn(receiving());
        when(eraService.blockTime(Era.Conway, 1000L)).thenThrow(new IllegalStateException("no era"));

        provider.refresh();

        assertThat(provider.current())
                .isEqualTo(new SyncStatusSnapshot(SyncState.SYNCED, 1000L, 10L, null, 1002L));
    }

    @Test
    void aFailingReadIsUnknown() {
        when(cursorService.getCursor()).thenThrow(new IllegalStateException("database is down"));

        provider.refresh();

        assertThat(provider.current()).isEqualTo(SyncStatusSnapshot.UNKNOWN);
    }

    @Test
    void withoutYaciStoreServicesIsUnknown() {
        SyncStatusProvider withoutServices = new SyncStatusProvider(providerOf(null), providerOf(null),
                providerOf(null), providerOf(null));

        withoutServices.refresh();

        assertThat(withoutServices.current()).isEqualTo(SyncStatusSnapshot.UNKNOWN);
    }

    private static SyncStatus sync(long slot, long networkSlot, boolean synced) {
        return SyncStatus.builder().slot(slot).block(slot / 100).networkSlot(networkSlot)
                .networkBlock(networkSlot == SyncStatus.UNKNOWN_NETWORK_TIP ? networkSlot : networkSlot / 100)
                .synced(synced).build();
    }

    private static HealthStatus receiving() {
        return HealthStatus.builder().isConnectionAlive(true).isReceivingBlocks(true).build();
    }

    private static HealthStatus notReceiving() {
        return HealthStatus.builder().isConnectionAlive(true).isReceivingBlocks(false).build();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }
}

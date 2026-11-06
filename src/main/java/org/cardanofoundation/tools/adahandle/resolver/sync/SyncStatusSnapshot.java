package org.cardanofoundation.tools.adahandle.resolver.sync;

import java.time.Instant;

/**
 * The sync status at one moment. Fields are null when not known.
 *
 * @param state     how current the data is
 * @param slot      slot of the last block indexed
 * @param block     number of the last block indexed
 * @param blockTime time of the last block indexed
 * @param tipSlot   slot of the network tip, as last fetched from the node
 */
public record SyncStatusSnapshot(SyncState state, Long slot, Long block, Instant blockTime, Long tipSlot) {

    public static final SyncStatusSnapshot UNKNOWN = new SyncStatusSnapshot(SyncState.UNKNOWN, null, null, null, null);
}

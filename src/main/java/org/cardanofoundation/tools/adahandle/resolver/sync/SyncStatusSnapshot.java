package org.cardanofoundation.tools.adahandle.resolver.sync;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * The sync status at one moment.
 *
 * @param state     how current the data is
 * @param slot      slot of the last block indexed. Null only in {@link #UNKNOWN}: no block indexed yet, the first
 *                  refresh hasn't run, or the status couldn't be read
 * @param block     number of the last block indexed. Null only in {@link #UNKNOWN}, like {@code slot}
 * @param blockTime time of the last block indexed. Null in {@link #UNKNOWN}, and when Yaci Store can't turn the slot
 *                  into a time: the cursor has no era, or the era's start isn't stored yet
 * @param tipSlot   slot of the network tip, as last fetched from the node. Null in {@link #UNKNOWN}, and before Yaci
 *                  Store has fetched the tip, i.e. just after start-up, or when fetching it failed
 */
public record SyncStatusSnapshot(SyncState state, @Nullable Long slot, @Nullable Long block, @Nullable Instant blockTime,
                                 @Nullable Long tipSlot) {

    public static final SyncStatusSnapshot UNKNOWN = new SyncStatusSnapshot(SyncState.UNKNOWN, null, null, null, null);
}

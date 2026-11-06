package org.cardanofoundation.tools.adahandle.resolver.sync;

/**
 * How current the resolver's data is, as reported in the X-Sync-* headers of every API response.
 */
public enum SyncState {
    /** At the network tip (within a few blocks) and still receiving blocks. */
    SYNCED,
    /** Catching up with the network tip: answers can be out of date. */
    SYNCING,
    /** No block received from the node for longer than store.block-receive-delay-seconds: answers can be out of date. */
    STALE,
    /** Nothing indexed yet, or the network tip is not known. */
    UNKNOWN;

    public String headerValue() {
        return name().toLowerCase();
    }
}

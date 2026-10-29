package org.cardanofoundation.tools.adahandle.resolver.repository;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdaHandleHistoryRepository extends JpaRepository<AdaHandleHistoryItem, String> {

    // flushAutomatically + clearAutomatically so a subsequent find* in the same tx reads
    // the post-delete state from the DB and isn't served a stale persistence-context entity.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AdaHandleHistoryItem WHERE slot > :target")
    int deleteAllAfterSlot(@Param("target") long target);

    /**
     * Distinct handle names that have history on the abandoned fork (slot &gt; target).
     * Backed by {@code idx_ada_handle_history_item_slot}; a shallow range scan on a real reorg.
     */
    @Query("SELECT DISTINCT item.name FROM AdaHandleHistoryItem item WHERE item.slot > :target")
    List<String> findNamesWithSlotGreaterThan(@Param("target") long target);

    /**
     * Latest remaining history entry for a handle. With the composite PK {@code (name, slot)}
     * this resolves as a PK index seek to the last entry — O(log n) — instead of the previous
     * full-table {@code GROUP BY name, MAX(slot)} over all history rows.
     */
    AdaHandleHistoryItem findFirstByNameOrderBySlotDesc(String name);
}

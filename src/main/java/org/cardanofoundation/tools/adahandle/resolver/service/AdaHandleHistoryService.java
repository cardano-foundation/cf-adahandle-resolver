package org.cardanofoundation.tools.adahandle.resolver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleHistoryMapper;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdaHandleHistoryService {

    private final AdaHandleHistoryRepository adaHandleHistoryRepository;
    private final AdaHandleService adaHandleService;

    public void rollbackToSlot(long slot) {
        log.info("Rollback to slot {}", slot);

        // Capture the distinct handle names touched on the abandoned fork BEFORE deleting,
        // so we know exactly which ada_handle rows need repair. Backed by
        // idx_ada_handle_history_item_slot — a shallow range scan on a real reorg.
        List<String> affectedNames = adaHandleHistoryRepository.findNamesWithSlotGreaterThan(slot);

        int deleted = adaHandleHistoryRepository.deleteAllAfterSlot(slot);
        if (deleted == 0) {
            // No-op rollback (e.g. the node's rollbackward to the current cursor point at the
            // catch-up-to-tip transition). Nothing was removed, so the handle table is already
            // correct — skip the recalculation entirely.
            log.info("Rollback to slot {} deleted 0 history rows — nothing to do", slot);
            return;
        }

        log.info("Rollback to slot {} deleted {} history rows across {} handle(s)",
                slot, deleted, affectedNames.size());

        // Restore each affected handle to its latest REMAINING history entry (the pre-fork
        // owner), or drop it if it no longer has any history (it was first minted on the
        // abandoned fork) or that entry is a burn. This is O(affected handles) with a PK seek per handle — not the
        // previous O(all history) re-aggregation + O(all handles) full-table rewrite — so it
        // stays cheap even on a slow DB and never blocks the chainsync event-loop thread.
        for (String name : affectedNames) {
            AdaHandleHistoryItem latest = adaHandleHistoryRepository.findFirstByNameOrderBySlotDesc(name);
            if (latest == null || latest.isBurned()) {
                adaHandleService.deleteByName(name);
            } else {
                adaHandleService.upsert(AdaHandleHistoryMapper.toAdaHandle(latest));
            }
        }

        log.info("Finished rollback to slot {}", slot);
    }

    public void saveAll(List<AdaHandleHistoryItem> adaHandleHistoryItems) {
        adaHandleHistoryRepository.saveAll(adaHandleHistoryItems);
    }
}

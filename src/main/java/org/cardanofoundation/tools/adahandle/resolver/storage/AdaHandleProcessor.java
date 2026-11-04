package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.MintBurnEvent;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItemKey;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleHistoryMapper;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleMapper;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandlePolicies;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdaHandleProcessor {
    private final AdaHandleService adaHandleService;
    private final AdaHandleHistoryService adaHandleHistoryService;

    /**
     * The handle changes of the blocks Yaci Store has delivered but not committed yet, as history rows: an owner for
     * each output carrying a handle token, a burn for each burned one. During the initial sync Yaci Store processes a
     * batch of blocks in parallel and delivers their events in no particular order, so the changes are only applied
     * on its CommitEvent, in chain order.
     */
    private final List<AdaHandleHistoryItem> pendingChanges = Collections.synchronizedList(new ArrayList<>());

    @EventListener
    public void processAdaHandle(AddressUtxoEvent addressUtxoEvent) {
        long slot = addressUtxoEvent.getMetadata().getSlot();
        pendingChanges.addAll(addressUtxoEvent.getTxInputOutputs().stream()
                .flatMap(txInputOutput -> txInputOutput.getOutputs().stream())
                .filter(this::includesAdaHandle)
                .flatMap(addressUtxo -> AdaHandleHistoryMapper.fromAddressUtxoEntities(addressUtxo).stream())
                .map(change -> {
                    change.setSlot(slot);
                    return change;
                })
                .toList());
    }

    @EventListener
    public void processBurns(MintBurnEvent mintBurnEvent) {
        long slot = mintBurnEvent.getMetadata().getSlot();
        pendingChanges.addAll(mintBurnEvent.getTxMintBurns().stream()
                .filter(txMintBurn -> txMintBurn.getAmounts() != null)
                .flatMap(txMintBurn -> txMintBurn.getAmounts().stream())
                .map(amount -> AdaHandleMapper.burn(amount, slot))
                .filter(Objects::nonNull)
                .toList());
    }

    public boolean includesAdaHandle(AddressUtxo addressUtxo) {
        List<Amt> amounts = addressUtxo.getAmounts();

        if (amounts != null) {
            for (final Amt amount : amounts) {
                if (AdaHandlePolicies.contains(amount.getPolicyId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Applies the pending changes in chain order: block by block, each handle's state at the end of the block (see
     * {@link #endOfBlockState}) goes to the history, and each handle's last state to ada_handle.
     * <p>
     * Invoked once per batch of blocks (100) during the initial sync, and for each block at the tip.
     */
    /**
     * A handle's state at the end of a block, from its changes in that block. Yaci Store doesn't say in which order a
     * block's transactions came, but a burned token does: an output carrying the burned token came before the burn,
     * since a burned token no longer exists, and an output carrying another token of the same name came after it. That
     * is how legacy handles were migrated to CIP-68: one transaction burns the legacy token and mints the CIP-68 token,
     * and the handle lives on. Kora Labs applies a transaction's burns before its outputs, which agrees.
     */
    static AdaHandleHistoryItem endOfBlockState(List<AdaHandleHistoryItem> changes) {
        Set<String> burnedTokens = changes.stream()
                .filter(AdaHandleHistoryItem::isBurned)
                .map(AdaHandleHistoryItem::getToken)
                .collect(Collectors.toSet());
        if (burnedTokens.isEmpty()) {
            return changes.getLast();
        }
        return changes.stream()
                .filter(change -> !change.isBurned() && !burnedTokens.contains(change.getToken()))
                .reduce((first, second) -> second)
                .orElseGet(() -> changes.stream().filter(AdaHandleHistoryItem::isBurned).findFirst().orElseThrow());
    }

    @EventListener
    @Transactional
    public void handleCommitEvent(CommitEvent commitEvent) {
        List<AdaHandleHistoryItem> changes;
        synchronized (pendingChanges) {
            changes = new ArrayList<>(pendingChanges);
            pendingChanges.clear();
        }
        if (changes.isEmpty()) {
            return;
        }

        // Stable sort: a block's outputs keep their transaction order.
        changes.sort(Comparator.comparingLong(AdaHandleHistoryItem::getSlot));
        // The history is keyed by (name, slot): one row per handle and block.
        List<AdaHandleHistoryItem> blockStates = changes.stream()
                .collect(Collectors.groupingBy(change -> new AdaHandleHistoryItemKey(change.getName(), change.getSlot()),
                        LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(AdaHandleProcessor::endOfBlockState)
                .toList();
        adaHandleHistoryService.saveAll(blockStates);

        Map<String, AdaHandleHistoryItem> lastChanges = new LinkedHashMap<>();
        blockStates.forEach(change -> lastChanges.put(change.getName(), change));
        adaHandleService.saveAll(lastChanges.values().stream()
                .filter(change -> !change.isBurned())
                .map(AdaHandleHistoryMapper::toAdaHandle)
                .toList());
        adaHandleService.deleteAllByName(lastChanges.values().stream()
                .filter(AdaHandleHistoryItem::isBurned)
                .map(AdaHandleHistoryItem::getName)
                .toList());
    }

    @EventListener
    @Transactional
    public void handleRollback(RollbackEvent rollbackEvent) {
        log.info("Handle rollback");
        long rollbackSlot = rollbackEvent.getRollbackTo().getSlot();
        // Changes of rolled-back blocks have not been committed yet; drop them so the next CommitEvent does not write
        // the abandoned fork's owners or burns.
        pendingChanges.removeIf(change -> change.getSlot() > rollbackSlot);
        adaHandleHistoryService.rollbackToSlot(rollbackSlot);
    }

}

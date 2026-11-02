package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.MintBurnEvent;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleMapper;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandlePolicies;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdaHandleProcessor {
    private final AdaHandleService adaHandleService;
    private final AdaHandleHistoryService adaHandleHistoryService;

    private List<SlotAddressUtxo> slotAddressUtxosCache = Collections.synchronizedList(new ArrayList<>());
    private List<SlotBurns> slotBurnsCache = Collections.synchronizedList(new ArrayList<>());


    @EventListener
    public void processAdaHandle(AddressUtxoEvent addressUtxoEvent) {
        var addressUtxoList = addressUtxoEvent.getTxInputOutputs()
                .stream().flatMap(txInputOutput -> txInputOutput.getOutputs().stream())
                .filter(this::includesAdaHandle).toList();

        if (addressUtxoList.isEmpty()) {
            return;
        }

        var slotAddressUtxo = new SlotAddressUtxo(addressUtxoEvent.getMetadata().getSlot(), addressUtxoList);
        slotAddressUtxosCache.add(slotAddressUtxo);
    }

    @EventListener
    public void processBurns(MintBurnEvent mintBurnEvent) {
        List<String> burned = mintBurnEvent.getTxMintBurns().stream()
                .filter(txMintBurn -> txMintBurn.getAmounts() != null)
                .flatMap(txMintBurn -> txMintBurn.getAmounts().stream())
                .map(AdaHandleMapper::burnedHandleName)
                .filter(Objects::nonNull)
                .toList();

        if (burned.isEmpty()) {
            return;
        }

        slotBurnsCache.add(new SlotBurns(mintBurnEvent.getMetadata().getSlot(), burned));
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
     * This is invoked once per block batch (100 blocks) at the end of the block processing during initial sync when
     * parallel processing is enabled.
     * <p>
     * After the sync reaches tip, batch size is 1 and this method is invoked for each block.
     */
    @EventListener
    @Transactional
    public void handleCommitEvent(CommitEvent commitEvent) {

        if (slotAddressUtxosCache.isEmpty() && slotBurnsCache.isEmpty()) {
            return;
        }

        // Process the blocks in slot order. Within a block, burns come after its outputs: a token
        // is spent (and possibly moved) before it can be burned, so a burn is the block's last word.
        TreeMap<Long, List<AddressUtxo>> outputsBySlot = new TreeMap<>();
        TreeMap<Long, List<String>> burnsBySlot = new TreeMap<>();
        synchronized (slotAddressUtxosCache) {
            slotAddressUtxosCache.forEach(entry -> outputsBySlot
                    .computeIfAbsent(entry.slot(), slot -> new ArrayList<>()).addAll(entry.addressUtxos()));
        }
        synchronized (slotBurnsCache) {
            slotBurnsCache.forEach(entry -> burnsBySlot
                    .computeIfAbsent(entry.slot(), slot -> new ArrayList<>()).addAll(entry.names()));
        }
        TreeSet<Long> slots = new TreeSet<>(outputsBySlot.keySet());
        slots.addAll(burnsBySlot.keySet());

        try {
            for (long slot : slots) {
                List<AddressUtxo> addressUtxos = outputsBySlot.get(slot);
                if (addressUtxos != null) {
                    adaHandleService.saveAllAdaHandles(addressUtxos);
                    adaHandleHistoryService.saveAdaHandleHistoryItems(addressUtxos);
                }
                List<String> burned = burnsBySlot.get(slot);
                if (burned != null) {
                    adaHandleService.deleteAllByName(burned);
                    adaHandleHistoryService.saveBurns(burned, slot);
                }
            }
        } finally {
            // Clear the caches after processing
            slotAddressUtxosCache.clear();
            slotBurnsCache.clear();
        }
    }

    @EventListener
    @Transactional
    public void handleRollback(RollbackEvent rollbackEvent) {
        log.info("Handle rollback");
        long rollbackSlot = rollbackEvent.getRollbackTo().getSlot();
        // UTxOs cached from rolled-back blocks have not been committed yet; drop them so the
        // next CommitEvent does not write the abandoned fork's owners.
        slotAddressUtxosCache.removeIf(slotAddressUtxo -> slotAddressUtxo.slot() > rollbackSlot);
        slotBurnsCache.removeIf(slotBurns -> slotBurns.slot() > rollbackSlot);
        adaHandleHistoryService.rollbackToSlot(rollbackSlot);
    }

}

record SlotAddressUtxo(long slot, List<AddressUtxo> addressUtxos) {
}

record SlotBurns(long slot, List<String> names) {
}

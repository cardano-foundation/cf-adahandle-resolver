package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import lombok.RequiredArgsConstructor;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdaHandleProcessor {
    private final AdaHandleService adaHandleService;
    private final AdaHandleHistoryService adaHandleHistoryService;

    private List<SlotAddressUtxo> slotAddressUtxosCache = Collections.synchronizedList(new ArrayList<>());


    @EventListener
    public void processAdaHandle(AddressUtxoEvent addressUtxoEvent) {
        var addressUtxoList = addressUtxoEvent.getTxInputOutputs()
                .stream().flatMap(txInputOutput -> txInputOutput.getOutputs().stream())
                .filter(this::includesAdaHandle).toList();

        if (addressUtxoList.isEmpty()) {
            return;
        }

        var slotAddressUtxo = new SlotAddressUtxo(addressUtxoEvent.getEventMetadata().getSlot(), addressUtxoList);
        slotAddressUtxosCache.add(slotAddressUtxo);
    }

    public boolean includesAdaHandle(AddressUtxo addressUtxo) {
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        List<Amt> amounts = addressUtxo.getAmounts();

        if (amounts != null) {
            for (final Amt amount : amounts) {
                if (amount.getPolicyId() != null && amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
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

        if (slotAddressUtxosCache.isEmpty()) {
            return;
        }

        //Sort slotAddressdUtxosCache by slot in ascending order to process the utxos in the correct order
        slotAddressUtxosCache.sort((o1, o2) -> Long.compare(o1.slot(), o2.slot()));

        try {
            // Process the cached slotAddressUtxos
            for (SlotAddressUtxo slotAddressUtxo : slotAddressUtxosCache) {
                adaHandleService.saveAllAdaHandles(slotAddressUtxo.addressUtxos());
                adaHandleHistoryService.saveAdaHandleHistoryItems(slotAddressUtxo.addressUtxos());
            }

        } finally {
            // Clear the cache after processing
            slotAddressUtxosCache.clear();
        }
    }

    @EventListener
    @Transactional
    public void handleRollback(RollbackEvent rollbackEvent) {
        adaHandleHistoryService.rollbackToSlot(rollbackEvent.getRollbackTo().getSlot());
    }

}

record SlotAddressUtxo(long slot, List<AddressUtxo> addressUtxos) {
}

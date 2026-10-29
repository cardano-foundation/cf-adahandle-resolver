package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.TxInputOutput;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AdaHandleProcessorTest {

    private static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";

    @Mock
    private AdaHandleService adaHandleService;
    @Mock
    private AdaHandleHistoryService adaHandleHistoryService;
    @InjectMocks
    private AdaHandleProcessor adaHandleProcessor;

    @Test
    public void testRollbackDropsUncommittedUtxosFromRolledBackBlocks() {
        AddressUtxo kept = handleUtxo("addr1kept");
        AddressUtxo onFork = handleUtxo("addr1fork");
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(1000L, kept));
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(2000L, onFork));

        // Rollback to slot 1500 arrives before the batch is committed.
        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(1500L, "hash")).build());
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(1500L).build(), List.of()));

        verify(adaHandleHistoryService).rollbackToSlot(1500L);
        verify(adaHandleService).saveAllAdaHandles(List.of(kept));
        verify(adaHandleService, never()).saveAllAdaHandles(List.of(onFork));
        verify(adaHandleHistoryService, never()).saveAdaHandleHistoryItems(List.of(onFork));
    }

    @Test
    public void testRollbackBelowAllCachedSlotsLeavesNothingToCommit() {
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(2000L, handleUtxo("addr1fork")));

        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(1000L, "hash")).build());
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(1000L).build(), List.of()));

        verify(adaHandleService, never()).saveAllAdaHandles(anyList());
        verify(adaHandleHistoryService, never()).saveAdaHandleHistoryItems(anyList());
    }

    private static AddressUtxo handleUtxo(String ownerAddr) {
        AddressUtxo addressUtxo = new AddressUtxo();
        addressUtxo.setOwnerAddr(ownerAddr);
        addressUtxo.setAmounts(List.of(Amt.builder().policyId(ADA_HANDLE_POLICY_ID).build()));
        return addressUtxo;
    }

    private static AddressUtxoEvent addressUtxoEvent(long slot, AddressUtxo output) {
        TxInputOutput txInputOutput = TxInputOutput.builder().outputs(List.of(output)).build();
        return new AddressUtxoEvent(EventMetadata.builder().slot(slot).build(), List.of(txInputOutput));
    }
}

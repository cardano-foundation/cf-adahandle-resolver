package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.core.model.Amount;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.MintBurnEvent;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.domain.TxMintBurn;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.TxInputOutput;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
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

    @Captor
    private ArgumentCaptor<List<AdaHandleHistoryItem>> history;
    @Captor
    private ArgumentCaptor<List<AdaHandle>> handles;
    @Captor
    private ArgumentCaptor<List<String>> deleted;

    @Test
    public void testRollbackDropsUncommittedChangesFromRolledBackBlocks() {
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(1000L, handleUtxo("kept", "addr1kept")));
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(2000L, handleUtxo("onfork", "addr1fork")));

        // Rollback to slot 1500 arrives before the batch is committed.
        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(1500L, "hash")).build());
        commit(1500L);

        verify(adaHandleHistoryService).rollbackToSlot(1500L);
        verify(adaHandleHistoryService).saveAll(history.capture());
        assertThat(history.getValue().stream().map(AdaHandleHistoryItem::getName).toList(), contains("kept"));
        verify(adaHandleService).saveAll(handles.capture());
        assertThat(handles.getValue().stream().map(AdaHandle::getPaymentAddress).toList(), contains("addr1kept"));
    }

    @Test
    public void testRollbackBelowAllPendingSlotsLeavesNothingToCommit() {
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(2000L, handleUtxo("onfork", "addr1fork")));

        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(1000L, "hash")).build());
        commit(1000L);

        verify(adaHandleService, never()).saveAll(anyList());
        verify(adaHandleHistoryService, never()).saveAll(anyList());
    }

    @Test
    public void testChangesAreAppliedInChainOrderWhateverOrderTheyArrive() {
        // Delivered out of order, as in a parallel batch: the burn of slot 300 and the transfer of slot 200 before the
        // mint of slot 100.
        adaHandleProcessor.processBurns(burnEvent(300L, "alice"));
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(200L, handleUtxo("alice", "addr1second")));
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(100L, handleUtxo("alice", "addr1first")));
        commit(300L);

        verify(adaHandleHistoryService).saveAll(history.capture());
        assertThat(history.getValue().stream().map(AdaHandleHistoryItem::getSlot).toList(), contains(100L, 200L, 300L));
        // Only the last state reaches ada_handle: burned.
        verify(adaHandleService).saveAll(handles.capture());
        assertThat(handles.getValue(), empty());
        verify(adaHandleService).deleteAllByName(deleted.capture());
        assertThat(deleted.getValue(), contains("alice"));
    }

    @Test
    public void testWithinABlockTheBurnComesAfterTheOutputs() {
        adaHandleProcessor.processBurns(burnEvent(100L, "bob"));
        adaHandleProcessor.processAdaHandle(addressUtxoEvent(100L, handleUtxo("bob", "addr1buyer")));
        commit(100L);

        verify(adaHandleHistoryService).saveAll(history.capture());
        List<AdaHandleHistoryItem> saved = history.getValue();
        assertThat(saved.getLast().isBurned(), is(true));
        verify(adaHandleService).deleteAllByName(deleted.capture());
        assertThat(deleted.getValue(), contains("bob"));
    }

    private void commit(long slot) {
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(slot).build(), List.of()));
    }

    private static AddressUtxo handleUtxo(String handle, String ownerAddr) {
        AddressUtxo addressUtxo = new AddressUtxo();
        addressUtxo.setOwnerAddr(ownerAddr);
        addressUtxo.setAmounts(List.of(Amt.builder()
                .policyId(ADA_HANDLE_POLICY_ID)
                .unit(ADA_HANDLE_POLICY_ID + HexFormat.of().formatHex(handle.getBytes()))
                .assetName(handle)
                .build()));
        return addressUtxo;
    }

    private static AddressUtxoEvent addressUtxoEvent(long slot, AddressUtxo output) {
        output.setSlot(slot);
        TxInputOutput txInputOutput = TxInputOutput.builder().outputs(List.of(output)).build();
        return new AddressUtxoEvent(EventMetadata.builder().slot(slot).build(), List.of(txInputOutput));
    }

    private static MintBurnEvent burnEvent(long slot, String handle) {
        Amount burned = Amount.builder()
                .policyId(ADA_HANDLE_POLICY_ID)
                .unit(ADA_HANDLE_POLICY_ID + HexFormat.of().formatHex(handle.getBytes()))
                .assetNameBytes(handle.getBytes())
                .quantity(BigInteger.ONE.negate())
                .build();
        TxMintBurn txMintBurn = TxMintBurn.builder().txHash("burn" + slot).amounts(List.of(burned)).build();
        return new MintBurnEvent(EventMetadata.builder().slot(slot).build(), List.of(txMintBurn));
    }
}

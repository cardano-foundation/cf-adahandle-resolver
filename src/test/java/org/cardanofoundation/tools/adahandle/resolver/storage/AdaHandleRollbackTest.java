package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.RollbackEvent;
import com.bloxbean.cardano.yaci.store.events.internal.CommitEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.AddressUtxoEvent;
import com.bloxbean.cardano.yaci.store.utxo.domain.TxInputOutput;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end rollback behaviour through the real processor, services and database.
 * <p>
 * Each test commits blocks one at a time, as the indexer does at the chain tip, then rolls back.
 * Tests use their own slot range (well above the data other tests seed) and their own
 * addresses, since rolling back to a slot deletes all history after it.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"disable-indexer", "test"})
public class AdaHandleRollbackTest {

    private static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
    private static final String CIP68_USER_LABEL = "000de140";
    private static final String CIP68_REFERENCE_LABEL = "000643b0";

    @Autowired
    private AdaHandleProcessor adaHandleProcessor;
    @Autowired
    private AdaHandleService adaHandleService;

    @Test
    public void testRollbackRestoresPreviousOwnerOfCip25Handle() {
        commitBlock(10_001_000L, "addr_cip25_owner_a", cip25("rollbacklegacy"));
        commitBlock(10_002_000L, "addr_cip25_owner_b", cip25("rollbacklegacy"));

        rollbackTo(10_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("rollbacklegacy").getPaymentAddress(), is("addr_cip25_owner_a"));
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip25_owner_b"), empty());
    }

    @Test
    public void testRollbackRestoresPreviousOwnerOfCip68Handle() {
        commitBlock(20_001_000L, "addr_cip68_owner_a", cip68(CIP68_USER_LABEL, "rollbackalice"));
        commitBlock(20_002_000L, "addr_cip68_owner_b", cip68(CIP68_USER_LABEL, "rollbackalice"));

        rollbackTo(20_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("rollbackalice").getPaymentAddress(), is("addr_cip68_owner_a"));
        // Only the decoded handle name, never a fingerprint row created by the repair.
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip68_owner_a"), contains("rollbackalice"));
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip68_owner_b"), empty());
    }

    @Test
    public void testRollbackRemovesCip68HandleMintedOnAbandonedFork() {
        commitBlock(30_002_000L, "addr_cip68_fork_minter", cip68(CIP68_USER_LABEL, "rollbackbob"));

        rollbackTo(30_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("rollbackbob"), nullValue());
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip68_fork_minter"), empty());
    }

    @Test
    public void testRollbackIgnoresCip68ReferenceTokens() {
        commitBlock(40_001_000L, "addr_cip68_holder", cip68(CIP68_USER_LABEL, "rollbackcarol"));
        commitBlock(40_001_100L, "addr_cip68_reference_script_a", cip68(CIP68_REFERENCE_LABEL, "rollbackcarol"));
        // Reference token moves (e.g. a personalization update) on the abandoned fork.
        commitBlock(40_002_000L, "addr_cip68_reference_script_b", cip68(CIP68_REFERENCE_LABEL, "rollbackcarol"));

        rollbackTo(40_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("rollbackcarol").getPaymentAddress(), is("addr_cip68_holder"));
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip68_reference_script_a"), empty());
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_cip68_reference_script_b"), empty());
    }

    private static Amt cip25(String handle) {
        return Amt.builder()
                .policyId(ADA_HANDLE_POLICY_ID)
                .unit(ADA_HANDLE_POLICY_ID + HexFormat.of().formatHex(handle.getBytes()))
                .assetName(handle)
                .build();
    }

    /** yaci reports CIP-68 asset names as a fingerprint, as their label bytes aren't valid UTF-8. */
    private static Amt cip68(String label, String handle) {
        return Amt.builder()
                .policyId(ADA_HANDLE_POLICY_ID)
                .unit(ADA_HANDLE_POLICY_ID + label + HexFormat.of().formatHex(handle.getBytes()))
                .assetName("asset1fingerprintof" + label + handle)
                .build();
    }

    private void commitBlock(long slot, String ownerAddr, Amt amount) {
        AddressUtxo addressUtxo = new AddressUtxo();
        addressUtxo.setOwnerAddr(ownerAddr);
        addressUtxo.setOwnerStakeAddr("stake_" + ownerAddr);
        addressUtxo.setSlot(slot);
        addressUtxo.setAmounts(List.of(amount));

        TxInputOutput txInputOutput = TxInputOutput.builder().outputs(List.of(addressUtxo)).build();
        adaHandleProcessor.processAdaHandle(new AddressUtxoEvent(EventMetadata.builder().slot(slot).build(), List.of(txInputOutput)));
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(slot).build(), List.of()));
    }

    private void rollbackTo(long slot) {
        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(slot, "hash")).build());
    }
}

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
import org.cardanofoundation.tools.adahandle.resolver.mapper.VirtualSubHandleDatumTest;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
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
    private static final String CIP68_VIRTUAL_LABEL = "00000000";

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

    @Test
    public void testRollbackRestoresPreviousAddressOfVirtualSubHandle() {
        // The token stays at the Handle script; the root's owner re-points the sub-handle by datum.
        String script = "addr1wxktka03n943759y4pcexpmftdhzsrrv8kcd2qs8cwgtdhgg6j4ux";
        String addressA = "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x";
        String addressB = "addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8";
        commitBlock(50_001_000L, script, cip68(CIP68_VIRTUAL_LABEL, "dave@rollback"), VirtualSubHandleDatumTest.cip68Datum(addressA));
        commitBlock(50_002_000L, script, cip68(CIP68_VIRTUAL_LABEL, "dave@rollback"), VirtualSubHandleDatumTest.cip68Datum(addressB));
        assertThat(adaHandleService.getAddressesByAdaHandle("dave@rollback").getPaymentAddress(), is(addressB));

        rollbackTo(50_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("dave@rollback").getPaymentAddress(), is(addressA));
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress(addressB), empty());
        // The script holding the token is never reported as the owner.
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress(script), empty());
    }

    @Test
    public void testBurnRemovesHandlesOfEveryKind() {
        String virtualOwner = "addr1qx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3n0d3vllmyqwsx5wktcd8cc3sq835lu7drv2xwl2wywfgse35a3x";
        commitBlock(60_001_000L, "addr_burn_holder", cip25("burnlegacy"));
        commitBlock(60_001_100L, "addr_burn_holder", cip68(CIP68_USER_LABEL, "burnalice"));
        commitBlock(60_001_200L, "addr_burn_holder", cip68(CIP68_USER_LABEL, "sub@burnalice"));
        commitBlock(60_001_300L, "addr1wxktka03n943759y4pcexpmftdhzsrrv8kcd2qs8cwgtdhgg6j4ux",
                cip68(CIP68_VIRTUAL_LABEL, "virtual@burnalice"), VirtualSubHandleDatumTest.cip68Datum(virtualOwner));

        burnBlock(60_002_000L, burned("", "burnlegacy"), burned(CIP68_USER_LABEL, "burnalice"),
                burned(CIP68_USER_LABEL, "sub@burnalice"), burned(CIP68_VIRTUAL_LABEL, "virtual@burnalice"));

        for (String handle : List.of("burnlegacy", "burnalice", "sub@burnalice", "virtual@burnalice")) {
            assertThat(handle, adaHandleService.getAddressesByAdaHandle(handle), nullValue());
        }
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_burn_holder"), empty());
        // Other tests' virtual sub-handles resolve to the same address.
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress(virtualOwner), not(hasItem("virtual@burnalice")));
    }

    @Test
    public void testBurningReferenceOrSettingsTokenKeepsTheHandle() {
        commitBlock(61_001_000L, "addr_burn_ref_holder", cip68(CIP68_USER_LABEL, "burnref"));

        burnBlock(61_002_000L, burned(CIP68_REFERENCE_LABEL, "burnref"), burned("00001070", "burnref"));

        assertThat(adaHandleService.getAddressesByAdaHandle("burnref").getPaymentAddress(), is("addr_burn_ref_holder"));
    }

    @Test
    public void testHandleTransferredAndBurnedInOneBlockIsRemoved() {
        commitBlock(62_001_000L, "addr_burn_seller", cip68(CIP68_USER_LABEL, "burnsameblock"));

        addOutput(62_002_000L, "addr_burn_buyer", cip68(CIP68_USER_LABEL, "burnsameblock"), null);
        burnBlock(62_002_000L, burned(CIP68_USER_LABEL, "burnsameblock"));

        assertThat(adaHandleService.getAddressesByAdaHandle("burnsameblock"), nullValue());
        assertThat(adaHandleService.getAdaHandlesByPaymentAddress("addr_burn_buyer"), empty());
    }

    @Test
    public void testLegacyHandleMigratedToCip68InOneBlockLivesOn() {
        // 415 mainnet handles: one transaction burns the legacy token and mints the CIP-68 token of the same name.
        commitBlock(66_001_000L, "addr_migration_owner", cip25("migratedhandle"));

        addOutput(66_002_000L, "addr_migration_owner_cip68", cip68(CIP68_USER_LABEL, "migratedhandle"), null);
        burnBlock(66_002_000L, burned("", "migratedhandle"));

        assertThat(adaHandleService.getAddressesByAdaHandle("migratedhandle").getPaymentAddress(),
                is("addr_migration_owner_cip68"));

        // Rolling the migration back restores the legacy handle.
        rollbackTo(66_001_500L);
        assertThat(adaHandleService.getAddressesByAdaHandle("migratedhandle").getPaymentAddress(),
                is("addr_migration_owner"));
    }

    @Test
    public void testRollbackPastBurnRestoresTheOwner() {
        commitBlock(63_001_000L, "addr_burn_owner", cip68(CIP68_USER_LABEL, "burnrollback"));
        burnBlock(63_002_000L, burned(CIP68_USER_LABEL, "burnrollback"));
        assertThat(adaHandleService.getAddressesByAdaHandle("burnrollback"), nullValue());

        rollbackTo(63_001_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("burnrollback").getPaymentAddress(), is("addr_burn_owner"));
    }

    @Test
    public void testRollbackToAfterBurnKeepsTheHandleBurned() {
        commitBlock(64_001_000L, "addr_burn_first_owner", cip25("burnremint"));
        burnBlock(64_002_000L, burned("", "burnremint"));
        // Minted again, on the fork that is abandoned.
        commitBlock(64_003_000L, "addr_burn_second_owner", cip25("burnremint"));
        assertThat(adaHandleService.getAddressesByAdaHandle("burnremint").getPaymentAddress(), is("addr_burn_second_owner"));

        rollbackTo(64_002_500L);

        assertThat(adaHandleService.getAddressesByAdaHandle("burnremint"), nullValue());
    }

    @Test
    public void testHandleMintedAgainAfterBurnResolves() {
        commitBlock(65_001_000L, "addr_burn_old_owner", cip25("burnagain"));
        burnBlock(65_002_000L, burned("", "burnagain"));

        commitBlock(65_003_000L, "addr_burn_new_owner", cip25("burnagain"));

        assertThat(adaHandleService.getAddressesByAdaHandle("burnagain").getPaymentAddress(), is("addr_burn_new_owner"));
    }

    /** A burned amount of a mint, as yaci reports it: negative quantity, the asset name's bytes. */
    private static Amount burned(String label, String handle) {
        String assetNameHex = label + HexFormat.of().formatHex(handle.getBytes());
        return Amount.builder()
                .policyId(ADA_HANDLE_POLICY_ID)
                .unit(ADA_HANDLE_POLICY_ID + assetNameHex)
                .assetNameBytes(HexFormat.of().parseHex(assetNameHex))
                .quantity(BigInteger.ONE.negate())
                .build();
    }

    private void burnBlock(long slot, Amount... burns) {
        TxMintBurn txMintBurn = TxMintBurn.builder().txHash("burn" + slot).amounts(List.of(burns)).build();
        adaHandleProcessor.processBurns(new MintBurnEvent(EventMetadata.builder().slot(slot).build(), List.of(txMintBurn)));
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(slot).build(), List.of()));
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
        commitBlock(slot, ownerAddr, amount, null);
    }

    private void commitBlock(long slot, String ownerAddr, Amt amount, String inlineDatum) {
        addOutput(slot, ownerAddr, amount, inlineDatum);
        adaHandleProcessor.handleCommitEvent(new CommitEvent<>(EventMetadata.builder().slot(slot).build(), List.of()));
    }

    /** An output of a block that isn't committed yet. */
    private void addOutput(long slot, String ownerAddr, Amt amount, String inlineDatum) {
        AddressUtxo addressUtxo = new AddressUtxo();
        addressUtxo.setInlineDatum(inlineDatum);
        addressUtxo.setOwnerAddr(ownerAddr);
        addressUtxo.setOwnerStakeAddr("stake_" + ownerAddr);
        addressUtxo.setSlot(slot);
        addressUtxo.setAmounts(List.of(amount));

        TxInputOutput txInputOutput = TxInputOutput.builder().outputs(List.of(addressUtxo)).build();
        adaHandleProcessor.processAdaHandle(new AddressUtxoEvent(EventMetadata.builder().slot(slot).build(), List.of(txInputOutput)));
    }

    private void rollbackTo(long slot) {
        adaHandleProcessor.handleRollback(RollbackEvent.builder().rollbackTo(new Point(slot, "hash")).build());
    }
}

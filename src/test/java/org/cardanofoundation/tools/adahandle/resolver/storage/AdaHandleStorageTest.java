package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.common.domain.TxInput;
import com.bloxbean.cardano.yaci.store.utxo.storage.UtxoStorage;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * The resolver keeps only its own handle tables. AdaHandleStorage replaces Yaci Store's UTxO storage so that no
 * outputs or spent inputs are stored: on mainnet those tables would be by far the largest in the database.
 * <p>
 * Runs without the disable-indexer profile, because AdaHandleStorage only exists when the indexer does, but with the
 * sync switched off, so no node is contacted.
 */
@SpringBootTest(properties = "store.sync-auto-start=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class AdaHandleStorageTest {

    private static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";

    @Autowired
    private UtxoStorage utxoStorage;
    @Autowired
    private UtxoRepository utxoRepository;
    @Autowired
    private TxInputRepository txInputRepository;

    @BeforeEach
    public void emptyTables() {
        utxoRepository.deleteAll();
        txInputRepository.deleteAll();
    }

    @Test
    public void testYaciStoreUsesAdaHandleStorage() {
        // Yaci Store creates its own UtxoStorage only when there is none (@ConditionalOnMissingBean).
        assertThat(utxoStorage, instanceOf(AdaHandleStorage.class));
    }

    @Test
    public void testOutputsAreNotStored() {
        utxoStorage.saveUnspent(List.of(handleOutput(1000L, 0, "alice"), plainOutput(1000L, 1)));

        assertThat(utxoRepository.findAll(), empty());
        assertThat(utxoStorage.findById("tx1000", 0).isPresent(), is(false));
        assertThat(utxoStorage.findById("tx1000", 1).isPresent(), is(false));
    }

    @Test
    public void testSpentInputsAreNotStored() {
        utxoStorage.saveSpent(List.of(TxInput.builder()
                .txHash("tx1000").outputIndex(0)
                .spentAtSlot(2000L).spentAtBlock(20L).spentAtBlockHash("block2000").spentTxHash("tx2000")
                .build()));

        assertThat(txInputRepository.findAll(), empty());
    }

    @Test
    public void testRollbackHasNothingToDelete() {
        assertThat(utxoStorage.deleteUnspentBySlotGreaterThan(0L), is(0));
        assertThat(utxoStorage.deleteSpentBySlotGreaterThan(0L), is(0));
    }

    private static AddressUtxo handleOutput(long slot, int outputIndex, String handle) {
        AddressUtxo output = plainOutput(slot, outputIndex);
        output.setAmounts(List.of(
                Amt.builder().unit("lovelace").quantity(BigInteger.valueOf(2_000_000)).build(),
                Amt.builder()
                        .policyId(ADA_HANDLE_POLICY_ID)
                        .unit(ADA_HANDLE_POLICY_ID + HexFormat.of().formatHex(handle.getBytes()))
                        .assetName(handle)
                        .quantity(BigInteger.ONE)
                        .build()));
        return output;
    }

    private static AddressUtxo plainOutput(long slot, int outputIndex) {
        return AddressUtxo.builder()
                .txHash("tx" + slot).outputIndex(outputIndex).slot(slot).blockHash("block" + slot)
                .ownerAddr("addr1q9storagetest").lovelaceAmount(BigInteger.valueOf(2_000_000))
                .build();
    }
}

package org.cardanofoundation.tools.adahandle.resolver.it;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.cardanofoundation.tools.adahandle.resolver.it.Devnet.Owner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.cip68;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.hex;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.referenceDatum;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.HOLDER;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY_ADDRESS;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY_ID;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.RECEIVER;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.RUN_ID;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.awaitOwner;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.handlesByPaymentAddress;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.handlesByStakeAddress;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.resolve;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.submit;

/**
 * Virtual sub-handles (CIP-67 label 0): the token sits at a Handle script, and the sub-handle resolves to the address
 * in its datum, {@code extra.resolved_addresses.ada}. Also the root's sub-handle settings token (label 1), which is
 * not a handle.
 */
class VirtualSubHandleIT {

    static final String ROOT = "it" + RUN_ID + "-vroot";
    static final String VIRTUAL = "v@" + ROOT;
    static final String VIRTUAL_ENTERPRISE = "ve@" + ROOT;
    /** Held by a wallet (MINTER's enterprise address) rather than a script; re-sent to the script with a new datum. */
    static final String VIRTUAL_UPDATED = "vu@" + ROOT;

    /** Who each handle resolves to once every test of this class ran (checked again after a restart). */
    static final Map<String, Owner> FINAL_OWNERS = Map.of(
            ROOT, Owner.baseAddressOf(HOLDER),
            VIRTUAL, Owner.baseAddressOf(RECEIVER),
            VIRTUAL_ENTERPRISE, new Owner(RECEIVER.enterpriseAddress(), null),
            VIRTUAL_UPDATED, Owner.baseAddressOf(HOLDER));

    /**
     * Mints and updates the virtual sub-handles. A fresh wallet: HOLDER's UTxOs are full of the other tests' tokens.
     * Each virtual sub-handle is minted in a transaction of its own, so it gets a UTxO (and a datum) of its own, as on
     * mainnet: outputs to the same address within one transaction are merged into one.
     */
    private static final Account MINTER = new Account(Networks.testnet());

    private static final String LABEL_0 = "00000000";
    private static final String LABEL_1 = "00001070";
    private static final String LABEL_100 = "000643b0";
    private static final String LABEL_222 = "000de140";

    @BeforeAll
    static void mintVirtualSubHandles() {
        Devnet.waitForYaciStore();
        Devnet.waitForResolver();
        Devnet.fund(MINTER);
        // The enterprise address holding VIRTUAL_UPDATED also pays for re-sending it.
        Devnet.fund(MINTER.enterpriseAddress());

        submit(new ScriptTx()
                .mintAsset(POLICY, List.of(cip68(LABEL_222, ROOT)), PlutusData.unit(), HOLDER.baseAddress())
                .mintAsset(POLICY, List.of(cip68(LABEL_100, ROOT), cip68(LABEL_1, ROOT)), PlutusData.unit(),
                        POLICY_ADDRESS, referenceDatum(ROOT)), MINTER);
        mintVirtual(VIRTUAL, POLICY_ADDRESS, RECEIVER.baseAddress());
        mintVirtual(VIRTUAL_ENTERPRISE, POLICY_ADDRESS, RECEIVER.enterpriseAddress());
        mintVirtual(VIRTUAL_UPDATED, MINTER.enterpriseAddress(), RECEIVER.baseAddress());
    }

    private static void mintVirtual(String handle, String holder, String resolvedAddress) {
        submit(new ScriptTx().mintAsset(POLICY, List.of(cip68(LABEL_0, handle)), PlutusData.unit(), holder,
                virtualDatum(handle, resolvedAddress)), MINTER);
    }

    @Test
    void virtualSubHandleResolvesToTheAddressInItsDatum() {
        awaitOwner(VIRTUAL, Owner.baseAddressOf(RECEIVER));

        assertThat(resolve("$" + VIRTUAL)).contains(Owner.baseAddressOf(RECEIVER));
        assertThat(handlesByStakeAddress(RECEIVER.stakeAddress())).contains(VIRTUAL);
    }

    @Test
    void virtualSubHandleWithAnEnterpriseAddressHasNoStakeAddress() {
        awaitOwner(VIRTUAL_ENTERPRISE, new Owner(RECEIVER.enterpriseAddress(), null));
    }

    @Test
    void neitherTheScriptHoldingVirtualSubHandlesNorTheSettingsTokenIsAHandleOwner() {
        awaitOwner(VIRTUAL, Owner.baseAddressOf(RECEIVER));
        awaitOwner(ROOT, Owner.baseAddressOf(HOLDER));

        assertThat(handlesByPaymentAddress(POLICY_ADDRESS)).isEmpty();
        assertThat(handlesByStakeAddress(HOLDER.stakeAddress())).doesNotContain(VIRTUAL, VIRTUAL_ENTERPRISE);
    }

    @Test
    void virtualSubHandleFollowsANewDatum() {
        awaitOwner(VIRTUAL_UPDATED, Owner.baseAddressOf(RECEIVER));
        Devnet.awaitUtxoWith(MINTER.enterpriseAddress(), POLICY_ID + LABEL_0 + hex(VIRTUAL_UPDATED));

        Tx update = new Tx()
                .payToContract(POLICY_ADDRESS, Amount.asset(POLICY_ID + LABEL_0 + hex(VIRTUAL_UPDATED), 1),
                        virtualDatum(VIRTUAL_UPDATED, HOLDER.baseAddress()))
                .from(MINTER.enterpriseAddress());
        submit(update, MINTER);

        awaitOwner(VIRTUAL_UPDATED, Owner.baseAddressOf(HOLDER));
        assertThat(handlesByStakeAddress(RECEIVER.stakeAddress())).doesNotContain(VIRTUAL_UPDATED);
    }

    /** A virtual sub-handle datum like ADA Handle's: Constr 0 [metadata, version, {resolved_addresses: {ada: address}}]. */
    static PlutusData virtualDatum(String handle, String address) {
        MapPlutusData metadata = new MapPlutusData();
        metadata.put(BytesPlutusData.of("name"), BytesPlutusData.of("$" + handle));
        MapPlutusData resolvedAddresses = new MapPlutusData();
        resolvedAddresses.put(BytesPlutusData.of("ada"), BytesPlutusData.of(new Address(address).getBytes()));
        MapPlutusData extra = new MapPlutusData();
        extra.put(BytesPlutusData.of("resolved_addresses"), resolvedAddresses);
        return ConstrPlutusData.of(0, metadata, BigIntPlutusData.of(1), extra);
    }
}

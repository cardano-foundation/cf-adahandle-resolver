package org.cardanofoundation.tools.adahandle.resolver.it;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import org.cardanofoundation.tools.adahandle.resolver.it.Devnet.Owner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.cip68;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.hex;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.legacy;
import static org.cardanofoundation.tools.adahandle.resolver.it.AdaHandleIndexingIT.referenceDatum;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY_ADDRESS;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY_ID;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.RECEIVER;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.RUN_ID;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.awaitOwner;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.handlesByPaymentAddress;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.handlesByStakeAddress;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.submit;
import static org.cardanofoundation.tools.adahandle.resolver.it.VirtualSubHandleIT.virtualDatum;

/**
 * Burning a handle's token removes the handle, for every kind: legacy, CIP-68 (label 222), NFT
 * sub-handle and virtual sub-handle (label 0). AdaHandleRestartIT checks they stay removed after a restart.
 */
class HandleBurnIT {

    static final String LEGACY = "it" + RUN_ID + "-burnl";
    static final String CIP68 = "it" + RUN_ID + "-burn";
    static final String SUBHANDLE = "nb@" + CIP68;
    static final String VIRTUAL = "vb@" + CIP68;
    static final List<String> BURNED = List.of(LEGACY, CIP68, SUBHANDLE, VIRTUAL);

    private static final String LABEL_0 = "00000000";
    private static final String LABEL_100 = "000643b0";
    private static final String LABEL_222 = "000de140";

    /** Holds the handles until it burns them: a fresh wallet, so its UTxOs hold nothing else. */
    private static final Account MINTER = new Account(Networks.testnet());

    @BeforeAll
    static void mintHandles() {
        Devnet.waitForYaciStore();
        Devnet.waitForResolver();
        Devnet.fund(MINTER);

        submit(new ScriptTx()
                .mintAsset(POLICY, List.of(legacy(LEGACY), cip68(LABEL_222, CIP68), cip68(LABEL_222, SUBHANDLE)),
                        PlutusData.unit(), MINTER.baseAddress())
                .mintAsset(POLICY, List.of(cip68(LABEL_100, CIP68)), PlutusData.unit(), POLICY_ADDRESS, referenceDatum(CIP68)),
                MINTER);
        // In a transaction of its own, so it gets a UTxO (and a datum) of its own.
        submit(new ScriptTx().mintAsset(POLICY, List.of(cip68(LABEL_0, VIRTUAL)), PlutusData.unit(),
                MINTER.enterpriseAddress(), virtualDatum(VIRTUAL, RECEIVER.baseAddress())), MINTER);
    }

    @Test
    void burnedHandlesOfEveryKindNoLongerResolve() {
        Map<String, String> units = Map.of(
                LEGACY, POLICY_ID + hex(LEGACY),
                CIP68, POLICY_ID + LABEL_222 + hex(CIP68),
                SUBHANDLE, POLICY_ID + LABEL_222 + hex(SUBHANDLE),
                VIRTUAL, POLICY_ID + LABEL_0 + hex(VIRTUAL));
        awaitOwner(LEGACY, Owner.baseAddressOf(MINTER));
        awaitOwner(CIP68, Owner.baseAddressOf(MINTER));
        awaitOwner(SUBHANDLE, Owner.baseAddressOf(MINTER));
        awaitOwner(VIRTUAL, Owner.baseAddressOf(RECEIVER));

        // Spent explicitly: cardano-client-lib's UTxO selection skips the virtual sub-handle's (it has a datum).
        List<Utxo> utxos = List.of(
                Devnet.awaitUtxoWith(MINTER.baseAddress(), units.get(LEGACY)),
                Devnet.awaitUtxoWith(MINTER.baseAddress(), units.get(CIP68)),
                Devnet.awaitUtxoWith(MINTER.baseAddress(), units.get(SUBHANDLE)),
                Devnet.awaitUtxoWith(MINTER.enterpriseAddress(), units.get(VIRTUAL)))
                .stream().distinct().toList();
        List<Asset> burns = List.of(
                new Asset(LEGACY, BigInteger.ONE.negate()),
                new Asset("0x" + LABEL_222 + hex(CIP68), BigInteger.ONE.negate()),
                new Asset("0x" + LABEL_222 + hex(SUBHANDLE), BigInteger.ONE.negate()),
                new Asset("0x" + LABEL_0 + hex(VIRTUAL), BigInteger.ONE.negate()));
        submit(new ScriptTx()
                .collectFrom(utxos)
                .mintAsset(POLICY, burns, PlutusData.unit())
                .withChangeAddress(MINTER.baseAddress()), MINTER);

        BURNED.forEach(Devnet::awaitGone);
        assertThat(handlesByPaymentAddress(MINTER.baseAddress())).doesNotContainAnyElementsOf(BURNED);
        assertThat(handlesByStakeAddress(RECEIVER.stakeAddress())).doesNotContain(VIRTUAL);
    }
}

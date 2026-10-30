package org.cardanofoundation.tools.adahandle.resolver.it;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import org.cardanofoundation.tools.adahandle.resolver.it.Devnet.Owner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Mints handles on the devnet, legacy (CIP-25: the asset name is the handle) and CIP-68 (a label 222 user token with a
 * label 100 reference token), transfers some, and checks what the resolver's API returns for them.
 */
class AdaHandleIndexingIT {

    static final String LEGACY = "it" + RUN_ID + "-legacy";
    static final String CIP68 = "it" + RUN_ID + "-cip68";
    static final String ENTERPRISE = "it" + RUN_ID + "-ent";
    static final String LEGACY_MOVED = "it" + RUN_ID + "-legacy-mv";
    static final String CIP68_MOVED = "it" + RUN_ID + "-cip68-mv";

    /** Who holds each handle once every test of this class ran (checked again after a restart, see AdaHandleRestartIT). */
    static final Map<String, Owner> FINAL_OWNERS = Map.of(
            LEGACY, Owner.baseAddressOf(HOLDER),
            CIP68, Owner.baseAddressOf(HOLDER),
            ENTERPRISE, new Owner(HOLDER.enterpriseAddress(), null),
            LEGACY_MOVED, Owner.baseAddressOf(RECEIVER),
            CIP68_MOVED, Owner.baseAddressOf(RECEIVER));

    private static final String LABEL_100 = "000643b0";
    private static final String LABEL_222 = "000de140";

    @BeforeAll
    static void mintHandles() {
        Devnet.waitForYaciStore();
        Devnet.waitForResolver();
        Devnet.fund(HOLDER);

        ScriptTx mint = new ScriptTx()
                .mintAsset(POLICY, List.of(legacy(LEGACY), legacy(LEGACY_MOVED), cip68(LABEL_222, CIP68),
                        cip68(LABEL_222, CIP68_MOVED)), PlutusData.unit(), HOLDER.baseAddress())
                .mintAsset(POLICY, List.of(cip68(LABEL_100, CIP68)), PlutusData.unit(), POLICY_ADDRESS, referenceDatum(CIP68))
                .mintAsset(POLICY, List.of(cip68(LABEL_100, CIP68_MOVED)), PlutusData.unit(), POLICY_ADDRESS,
                        referenceDatum(CIP68_MOVED))
                .mintAsset(POLICY, List.of(legacy(ENTERPRISE)), PlutusData.unit(), HOLDER.enterpriseAddress());
        submit(mint, HOLDER);
    }

    @Test
    void legacyHandleResolvesToItsHolder() {
        awaitOwner(LEGACY, Owner.baseAddressOf(HOLDER));
    }

    @Test
    void cip68HandleResolvesToItsHolderAndItsReferenceTokenIsNoHandle() {
        awaitOwner(CIP68, Owner.baseAddressOf(HOLDER));

        assertThat(handlesByPaymentAddress(POLICY_ADDRESS)).isEmpty();
    }

    @Test
    void handleHeldByAnEnterpriseAddressHasNoStakeAddress() {
        awaitOwner(ENTERPRISE, new Owner(HOLDER.enterpriseAddress(), null));
    }

    @Test
    void handleResolvesWithLeadingDollarSign() {
        awaitOwner(LEGACY, Owner.baseAddressOf(HOLDER));

        assertThat(resolve("$" + LEGACY)).contains(Owner.baseAddressOf(HOLDER));
    }

    @Test
    void addressesListTheirHandles() {
        awaitOwner(LEGACY, Owner.baseAddressOf(HOLDER));
        awaitOwner(CIP68, Owner.baseAddressOf(HOLDER));

        assertThat(handlesByPaymentAddress(HOLDER.baseAddress())).contains(LEGACY, CIP68);
        assertThat(handlesByStakeAddress(HOLDER.stakeAddress())).contains(LEGACY, CIP68);
        assertThat(handlesByPaymentAddress(HOLDER.enterpriseAddress())).contains(ENTERPRISE);
    }

    @Test
    void transferredHandlesResolveToTheirNewHolder() {
        awaitOwner(LEGACY_MOVED, Owner.baseAddressOf(HOLDER));
        awaitOwner(CIP68_MOVED, Owner.baseAddressOf(HOLDER));

        Tx transfer = new Tx()
                .payToAddress(RECEIVER.baseAddress(), List.of(
                        Amount.asset(POLICY_ID + hex(LEGACY_MOVED), 1),
                        Amount.asset(POLICY_ID + LABEL_222 + hex(CIP68_MOVED), 1)))
                .from(HOLDER.baseAddress());
        submit(transfer, HOLDER);

        awaitOwner(LEGACY_MOVED, Owner.baseAddressOf(RECEIVER));
        awaitOwner(CIP68_MOVED, Owner.baseAddressOf(RECEIVER));
        assertThat(handlesByPaymentAddress(HOLDER.baseAddress())).doesNotContain(LEGACY_MOVED, CIP68_MOVED);
        assertThat(handlesByStakeAddress(RECEIVER.stakeAddress())).contains(LEGACY_MOVED, CIP68_MOVED);
    }

    @Test
    void unknownHandleIsNotFound() {
        assertThat(resolve("it" + RUN_ID + "-unknown")).isEmpty();
    }

    static Asset legacy(String handle) {
        return new Asset(handle, BigInteger.ONE);
    }

    static Asset cip68(String label, String handle) {
        return new Asset("0x" + label + hex(handle), BigInteger.ONE);
    }

    /** A CIP-68 reference token datum like the ones ADA Handle mints: Constr 0 [metadata, version, extra]. */
    static PlutusData referenceDatum(String handle) {
        MapPlutusData metadata = new MapPlutusData();
        metadata.put(BytesPlutusData.of("name"), BytesPlutusData.of("$" + handle));
        return ConstrPlutusData.of(0, metadata, BigIntPlutusData.of(1), new MapPlutusData());
    }

    static String hex(String handle) {
        return HexUtil.encodeHexString(handle.getBytes(StandardCharsets.UTF_8));
    }
}

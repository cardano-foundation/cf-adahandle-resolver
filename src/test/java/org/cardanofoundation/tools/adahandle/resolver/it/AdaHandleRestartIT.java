package org.cardanofoundation.tools.adahandle.resolver.it;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import org.cardanofoundation.tools.adahandle.resolver.it.Devnet.Owner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.HOLDER;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.POLICY;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.RUN_ID;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.awaitOwner;
import static org.cardanofoundation.tools.adahandle.resolver.it.Devnet.submit;

/**
 * Runs after AdaHandleIndexingIT, once the resolver was restarted (IT_AFTER_RESTART=true, same IT_RUN_ID). Yaci Store
 * publishes a rollback to its last block on every start; the handles indexed before the restart, CIP-68 ones included,
 * must survive it.
 */
@EnabledIfEnvironmentVariable(named = "IT_AFTER_RESTART", matches = "true")
class AdaHandleRestartIT {

    @Test
    void handlesIndexedBeforeTheRestartStillResolve() {
        Devnet.waitForYaciStore();
        Devnet.waitForResolver();

        // Once a handle minted now is indexed, the resolver has synced past the start-up rollback.
        String afterRestart = "it" + RUN_ID + "-restart";
        submit(new ScriptTx().mintAsset(POLICY, List.of(AdaHandleIndexingIT.cip68("000de140", afterRestart)),
                PlutusData.unit(), HOLDER.baseAddress()), HOLDER);
        awaitOwner(afterRestart, Owner.baseAddressOf(HOLDER));

        AdaHandleIndexingIT.FINAL_OWNERS.forEach(Devnet::awaitOwner);
    }
}

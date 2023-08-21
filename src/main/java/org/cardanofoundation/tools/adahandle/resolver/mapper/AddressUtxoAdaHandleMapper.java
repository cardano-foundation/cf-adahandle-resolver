package org.cardanofoundation.tools.adahandle.resolver.mapper;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;

import java.util.ArrayList;

public class AddressUtxoAdaHandleMapper {
    public static ArrayList<AdaHandle> toAdaHandles(AddressUtxoEntity addressUtxoEntity) {
        final ArrayList<AdaHandle> adaHandles = new ArrayList<>();
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        for (final Amt amount : addressUtxoEntity.getAmounts()) {
            if (amount.getPolicyId() != null) {
                if (amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
                    adaHandles.add(new AdaHandle(amount.getAssetName(), addressUtxoEntity.getOwnerStakeAddr(), addressUtxoEntity.getOwnerAddr()));
                }
            }
        }
        return adaHandles;
    }
}

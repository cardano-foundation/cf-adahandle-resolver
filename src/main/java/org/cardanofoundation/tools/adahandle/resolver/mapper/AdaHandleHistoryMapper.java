package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;

import java.util.ArrayList;

public class AdaHandleHistoryMapper {
    public static ArrayList<AdaHandleHistoryItem> fromAddressUtxoEntities(AddressUtxoEntity addressUtxoEntity) {
        final ArrayList<AdaHandleHistoryItem> adaHandleHistoryItems = new ArrayList<>();
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        if (addressUtxoEntity.getAmounts() != null) {
            for (final Amt amount : addressUtxoEntity.getAmounts()) {
                if (amount.getPolicyId() != null) {
                    if (amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
                        AdaHandleHistoryItem adaHandleHistoryItem = new AdaHandleHistoryItem();
                        adaHandleHistoryItem.setName(amount.getAssetName());
                        adaHandleHistoryItem.setStakeAddress(addressUtxoEntity.getOwnerStakeAddr());
                        adaHandleHistoryItem.setPaymentAddress(addressUtxoEntity.getOwnerAddr());
                        adaHandleHistoryItem.setSlot(addressUtxoEntity.getSlot());
                        adaHandleHistoryItems.add(adaHandleHistoryItem);
                    }
                }
            }
        }
        return adaHandleHistoryItems;
    }

    public static AdaHandle toAdaHandle(AdaHandleHistoryItem adaHandleHistoryItem) {
        return new AdaHandle(adaHandleHistoryItem.getName(), adaHandleHistoryItem.getStakeAddress(), adaHandleHistoryItem.getPaymentAddress());
    }
}

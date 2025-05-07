package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;

import java.util.ArrayList;

public class AdaHandleHistoryMapper {
    public static ArrayList<AdaHandleHistoryItem> fromAddressUtxoEntities(AddressUtxo addressUtxo) {
        final ArrayList<AdaHandleHistoryItem> adaHandleHistoryItems = new ArrayList<>();
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        if (addressUtxo.getAmounts() != null) {
            for (final Amt amount : addressUtxo.getAmounts()) {
                if (amount.getPolicyId() != null) {
                    if (amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
                        AdaHandleHistoryItem adaHandleHistoryItem = new AdaHandleHistoryItem();
                        adaHandleHistoryItem.setName(amount.getAssetName());
                        adaHandleHistoryItem.setStakeAddress(addressUtxo.getOwnerStakeAddr());
                        adaHandleHistoryItem.setPaymentAddress(addressUtxo.getOwnerAddr());
                        adaHandleHistoryItem.setSlot(addressUtxo.getSlot());
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

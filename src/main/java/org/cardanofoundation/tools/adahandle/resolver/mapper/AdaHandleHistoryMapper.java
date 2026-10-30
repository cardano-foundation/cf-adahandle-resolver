package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;

import java.util.ArrayList;

public class AdaHandleHistoryMapper {
    public static ArrayList<AdaHandleHistoryItem> fromAddressUtxoEntities(AddressUtxo addressUtxo) {
        final ArrayList<AdaHandleHistoryItem> adaHandleHistoryItems = new ArrayList<>();
        if (addressUtxo.getAmounts() != null) {
            for (final Amt amount : addressUtxo.getAmounts()) {
                // Same name and owner as ada_handle stores, so a rollback restores exactly that row.
                AdaHandle adaHandle = AdaHandleMapper.toAdaHandle(addressUtxo, amount);
                if (adaHandle != null) {
                    AdaHandleHistoryItem adaHandleHistoryItem = new AdaHandleHistoryItem();
                    adaHandleHistoryItem.setName(adaHandle.getName());
                    adaHandleHistoryItem.setStakeAddress(adaHandle.getStakeAddress());
                    adaHandleHistoryItem.setPaymentAddress(adaHandle.getPaymentAddress());
                    adaHandleHistoryItem.setSlot(addressUtxo.getSlot());
                    adaHandleHistoryItems.add(adaHandleHistoryItem);
                }
            }
        }
        return adaHandleHistoryItems;
    }

    public static AdaHandle toAdaHandle(AdaHandleHistoryItem adaHandleHistoryItem) {
        return new AdaHandle(adaHandleHistoryItem.getName(), adaHandleHistoryItem.getStakeAddress(), adaHandleHistoryItem.getPaymentAddress());
    }
}

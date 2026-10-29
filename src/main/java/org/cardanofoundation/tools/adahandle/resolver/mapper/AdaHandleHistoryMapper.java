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
                // Same name as ada_handle uses, so rollbacks can find the current row to repair.
                String adaHandle = AdaHandleMapper.toHandleName(amount);
                if (adaHandle != null) {
                    AdaHandleHistoryItem adaHandleHistoryItem = new AdaHandleHistoryItem();
                    adaHandleHistoryItem.setName(adaHandle);
                    adaHandleHistoryItem.setStakeAddress(addressUtxo.getOwnerStakeAddr());
                    adaHandleHistoryItem.setPaymentAddress(addressUtxo.getOwnerAddr());
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

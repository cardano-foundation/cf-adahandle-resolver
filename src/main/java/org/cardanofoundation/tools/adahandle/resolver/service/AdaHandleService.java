package org.cardanofoundation.tools.adahandle.resolver.service;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleHistoryMapper;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleMapper;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdaHandleService {
    @Autowired
    private AdaHandleRepository adaHandleRepository;

    public void saveAllAdaHandles(List<AddressUtxoEntity> addressUtxoList) {
        List<AdaHandle> adaHandles = addressUtxoList.stream()
                .map(AdaHandleMapper::toAdaHandles).flatMap(List::stream)
                .toList();

        adaHandleRepository.saveAll(adaHandles);
    }

    public String getStakeAddressByAdaHandle(String adaHandle) {
        if (adaHandle.startsWith("$")) {
            adaHandle = adaHandle.substring(1);
        }

        List<String> adaHandles = adaHandleRepository.findStakeAddressByAdaHandle(adaHandle);
        if (adaHandles.isEmpty()) {
            return null;
        } else {
            return adaHandles.get(0);
        }
    }

    public List<String> getAdaHandlesByStakeAddress(String stakeAddress) {
        return adaHandleRepository.findAdaHandlesByStakeAddress(stakeAddress);
    }

    public List<String> getAdaHandlesByPaymentAddress(String paymentAddress) {
        return adaHandleRepository.findAdaHandlesByPaymentAddress(paymentAddress);
    }

    public void recalculateAdaHandlesFromHistory(List<AdaHandleHistoryItem> adaHandleHistoryItems) {
        adaHandleRepository.deleteAll();
        List<AdaHandle> adaHandles = adaHandleHistoryItems.stream().map(AdaHandleHistoryMapper::toAdaHandle).toList();
        adaHandleRepository.saveAll(adaHandles);
    }
}

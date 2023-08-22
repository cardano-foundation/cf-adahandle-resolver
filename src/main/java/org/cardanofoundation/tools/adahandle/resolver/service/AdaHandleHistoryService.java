package org.cardanofoundation.tools.adahandle.resolver.service;

import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleHistoryMapper;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdaHandleHistoryService {

    @Autowired
    private AdaHandleHistoryRepository adaHandleHistoryRepository;

    @Autowired
    private AdaHandleService adaHandleService;

    public void rollbackToSlot(long slot) {
        adaHandleHistoryRepository.deleteAllAfterSlot(slot);
        List<AdaHandleHistoryItem> adaHandleHistoryItems = getLatestAdaHandleHistoryItemsByName();
        adaHandleService.recalculateAdaHandlesFromHistory(adaHandleHistoryItems);
    }

    public List<AdaHandleHistoryItem> getLatestAdaHandleHistoryItemsByName() {
        return  adaHandleHistoryRepository.getLatestHistoryItemByName();
    }

    public void saveAdaHandleHistoryItems(List<AddressUtxoEntity> addressUtxoList) {
        List<AdaHandleHistoryItem> adaHandleHistoryItems = addressUtxoList.stream()
                .map(AdaHandleHistoryMapper::fromAddressUtxoEntities).flatMap(List::stream).toList();
        adaHandleHistoryRepository.saveAll(adaHandleHistoryItems);
    }

    public void saveAll(List<AdaHandleHistoryItem> adaHandleHistoryItems) {
        adaHandleHistoryRepository.saveAll(adaHandleHistoryItems);
    }

    public void deleteAll() {
        adaHandleHistoryRepository.deleteAll();
    }
}

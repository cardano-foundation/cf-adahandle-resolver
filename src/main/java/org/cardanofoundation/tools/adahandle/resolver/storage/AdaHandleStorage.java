package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.common.domain.TxInput;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoCache;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.mapper.UtxoMapper;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.model.AddressUtxoEntity;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("!disable-indexer")
public class AdaHandleStorage extends UtxoStorageImpl {
    private final UtxoMapper mapper = UtxoMapper.INSTANCE;
    private final UtxoRepository utxoRepository;
    private final AdaHandleService adaHandleService;
    private final AdaHandleHistoryService adaHandleHistoryService;

    public AdaHandleStorage(UtxoRepository utxoRepository,
                            TxInputRepository spentOutputRepository,
                            DSLContext dsl, UtxoCache utxoCache,
                            AdaHandleService adaHandleService,
                            AdaHandleHistoryService adaHandleHistoryService) {
        super(utxoRepository, spentOutputRepository, dsl, utxoCache);
        this.utxoRepository = utxoRepository;
        this.adaHandleService = adaHandleService;
        this.adaHandleHistoryService = adaHandleHistoryService;
    }


    public boolean includesAdaHandle(AddressUtxoEntity addressUtxoEntity) {
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        List<Amt> amounts = addressUtxoEntity.getAmounts();

        if (amounts != null) {
            for (final Amt amount : amounts) {
                if (amount.getPolicyId() != null && amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void saveUnspent(List<AddressUtxo> addressUtxoList) {
        List<AddressUtxoEntity> addressUtxoEntities = addressUtxoList.stream()
                .map(mapper::toAddressUtxoEntity)
                .filter(this::includesAdaHandle).toList();

        if (!addressUtxoEntities.isEmpty()) {
            adaHandleService.saveAllAdaHandles(addressUtxoEntities);
            adaHandleHistoryService.saveAdaHandleHistoryItems(addressUtxoEntities);
        }

        utxoRepository.saveAll(new ArrayList<>());
    }

    @Override
    public void saveSpent(List<TxInput> txInputs) {
        super.saveSpent(txInputs);
    }

    @Override
    public int deleteUnspentBySlotGreaterThan(Long slot) {
        adaHandleHistoryService.rollbackToSlot(slot);
        return super.deleteUnspentBySlotGreaterThan(slot);
    }

}

package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.mapper.UtxoMapper;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.repository.UtxoRepository;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AdaHandleStorage extends UtxoStorageImpl {
    private final UtxoMapper mapper = UtxoMapper.INSTANCE;
    private final UtxoRepository utxoRepository;
    private final AdaHandleService adaHandleService;

    public AdaHandleStorage(UtxoRepository utxoRepository, DSLContext dsl, AdaHandleService adaHandleService) {
        super(utxoRepository, dsl);
        this.utxoRepository = utxoRepository;
        this.adaHandleService = adaHandleService;
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
    public Optional<List<AddressUtxo>> saveAll(List<AddressUtxo> addressUtxoList) {
        List<AddressUtxoEntity> addressUtxoEntities = addressUtxoList.stream()
                .map(mapper::toAddressUtxoEntity)
                .filter(this::includesAdaHandle).toList();

        if (!addressUtxoEntities.isEmpty()) {
            System.out.println(addressUtxoEntities.size());
            adaHandleService.saveAllAdaHandles(addressUtxoEntities);
        }

        addressUtxoEntities = utxoRepository.saveAll(addressUtxoEntities);
        return Optional.of(addressUtxoEntities.stream()
                .map(mapper::toAddressUtxo)
                .toList());
    }
}

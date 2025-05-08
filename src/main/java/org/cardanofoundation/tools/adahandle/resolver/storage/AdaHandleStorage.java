package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.TxInput;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoCache;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Dummy storage impl to disable storing unspent and spent outputs as we don't need them
 */
@Component
@Profile("!disable-indexer")
public class AdaHandleStorage extends UtxoStorageImpl {

    public AdaHandleStorage(UtxoRepository utxoRepository, TxInputRepository spentOutputRepository, DSLContext dsl, UtxoCache utxoCache, PlatformTransactionManager transactionManager) {
        super(utxoRepository, spentOutputRepository, dsl, utxoCache, transactionManager);
    }

    @Override
    public void saveUnspent(List<AddressUtxo> addressUtxoList) {
        //do nothing
    }


    @Override
    public void saveSpent(List<TxInput> txInputs) {
        //do nothing
    }

    @Override
    public int deleteUnspentBySlotGreaterThan(Long slot) {
        return 0;
    }

    @Override
    public int deleteSpentBySlotGreaterThan(Long slot) {
        return 0;
    }
}

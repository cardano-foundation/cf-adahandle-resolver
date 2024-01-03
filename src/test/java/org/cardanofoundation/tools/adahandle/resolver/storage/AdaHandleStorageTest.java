package org.cardanofoundation.tools.adahandle.resolver.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleHistoryRepository;
import org.cardanofoundation.tools.adahandle.resolver.repository.AdaHandleRepository;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@ComponentScan
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AdaHandleStorageTest {

    @Autowired
    private AdaHandleService adaHandleService;
    @Autowired
    private AdaHandleHistoryService adaHandleHistoryService;
    @Autowired
    private AdaHandleStorage adaHandleStorage;
    @Autowired
    private AdaHandleRepository adaHandleRepository;
    @Autowired
    private AdaHandleHistoryRepository adaHandleHistoryRepository;

    @BeforeEach
    public void cleanDatabase() {
        adaHandleRepository.deleteAll();
        adaHandleHistoryRepository.deleteAll();
    }

    @Test
    public void testIncludesAdaHandle() {
        ArrayList<Amt> amounts = new ArrayList<>();

        amounts.add(Amt.builder()
                .policyId("a0aa112227bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca5678")
                .assetName("SpaceBoy123")
                .build());

        amounts.add(Amt.builder()
                .assetName("lovelace")
                .build());

        AddressUtxoEntity addressUtxoEntity = AddressUtxoEntity.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(null)
                .spent(true)
                .slot(1201L)
                .build();

        assertThat(adaHandleStorage.includesAdaHandle(addressUtxoEntity), is(false));
        addressUtxoEntity.setSpent(null);
        assertThat(adaHandleStorage.includesAdaHandle(addressUtxoEntity), is(false));
        addressUtxoEntity.setAmounts(amounts);
        assertThat(adaHandleStorage.includesAdaHandle(addressUtxoEntity), is(false));

        amounts.add(Amt.builder()
                .policyId("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a")
                .assetName("Tom")
                .build());

        amounts.add(Amt.builder()
                .policyId("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a")
                .assetName("Henry")
                .build());

        assertThat(adaHandleStorage.includesAdaHandle(addressUtxoEntity), is(true));
    }

    @Test
    public void testSaveAll() {
        List<AddressUtxo> addressUtxoList = new ArrayList<>();
        addressUtxoList.add(AddressUtxo.builder()
                .ownerAddr("addr1u87ua2cf830jqwa3s59ds35pe4jnhupmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(null)
                .slot(1200L)
                .build());

        adaHandleStorage.saveAll(addressUtxoList);
        List<String> adaHandle = adaHandleService.getAdaHandlesByStakeAddress("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p");
        assertThat(adaHandle.size(), equalTo(0));

        ArrayList<Amt> amounts = new ArrayList<>();
        amounts.add(Amt.builder()
                .policyId("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a")
                .assetName("Tom")
                .build());

        addressUtxoList.add(AddressUtxo.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(amounts)
                .slot(1201L)
                .build());

        adaHandleStorage.saveAll(addressUtxoList);
        adaHandle = adaHandleService.getAdaHandlesByStakeAddress("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p");
        assertThat(adaHandle.size(), equalTo(1));
        assertThat(adaHandle, hasItems("Tom"));
    }
}

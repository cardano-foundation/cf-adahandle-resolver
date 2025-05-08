package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ActiveProfiles("[disable-indexer, test]")
public class AdaHandleHistoryMapperTest {

    @Test
    public void testFromAddressUtxoEntities() {
        ArrayList<AddressUtxo> addressUtxoEntities = new ArrayList<>();

        addressUtxoEntities.add(AddressUtxo.builder()
                .ownerAddr("addr1u87ua2cf830jqwa3s59ds35pe4jnhupmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(null)
                .slot(1200L)
                .build());

        ArrayList<Amt> amounts = new ArrayList<>();
        amounts.add(Amt.builder()
                .policyId("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a")
                .assetName("Tom")
                .build());

        amounts.add(Amt.builder()
                .policyId("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a")
                .assetName("Henry")
                .build());

        addressUtxoEntities.add(AddressUtxo.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(amounts)
                .slot(1201L)
                .build());

        amounts.add(Amt.builder()
                .policyId("a0aa112227bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca5678")
                .assetName("SpaceBoy123")
                .build());

        amounts.add(Amt.builder()
                .assetName("lovelace")
                .build());

        List<AdaHandleHistoryItem> adaHandles = addressUtxoEntities.stream()
                .map(AdaHandleHistoryMapper::fromAddressUtxoEntities).flatMap(List::stream)
                .toList();

        assertThat(adaHandles, hasSize(2));
        assertThat(adaHandles, hasItem(hasProperty("name", is("Tom"))));
        assertThat(adaHandles, hasItem(hasProperty("name", is("Henry"))));
    }
}

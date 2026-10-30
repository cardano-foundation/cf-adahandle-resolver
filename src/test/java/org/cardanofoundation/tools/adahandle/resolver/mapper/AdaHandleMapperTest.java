package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ActiveProfiles({"disable-indexer", "test"})
public class AdaHandleMapperTest {

    @Test
    public void testToAdaHandles() {
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

        amounts.add(Amt.builder()
                .policyId("a0aa112227bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca5678")
                .assetName("SpaceBoy123")
                .build());

        amounts.add(Amt.builder()
                .assetName("lovelace")
                .build());

        addressUtxoEntities.add(AddressUtxo.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(amounts)
                .slot(1201L)
                .build());

        List<AdaHandle> adaHandles = addressUtxoEntities.stream()
                .map(AdaHandleMapper::toAdaHandles).flatMap(List::stream)
                .toList();

        assertThat(adaHandles, hasSize(2));
        assertThat(adaHandles, hasItem(hasProperty("name", is("Tom"))));
        assertThat(adaHandles, hasItem(hasProperty("name", is("Henry"))));
    }

    @Test
    public void testToAdaHandlesDecodesCip68UserTokenAndSkipsReferenceToken() {
        String policyId = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        String hexName = HexFormat.of().formatHex("alice".getBytes());

        AddressUtxo addressUtxo = AddressUtxo.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(List.of(
                        // CIP-68 user token (label 222): yaci reports the asset name as a fingerprint
                        Amt.builder().policyId(policyId).unit(policyId + "000de140" + hexName).assetName("asset1fingerprintuser").build(),
                        // CIP-68 reference token (label 100): metadata only, not ownership
                        Amt.builder().policyId(policyId).unit(policyId + "000643b0" + hexName).assetName("asset1fingerprintref").build()))
                .slot(1300L)
                .build();

        List<AdaHandle> adaHandles = AdaHandleMapper.toAdaHandles(addressUtxo);

        assertThat(adaHandles, hasSize(1));
        assertThat(adaHandles.get(0).getName(), is("alice"));
    }

    private static final String POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
    // Handle script holding virtual sub-handle tokens on mainnet.
    private static final String HANDLE_SCRIPT = "addr1wxktka03n943759y4pcexpmftdhzsrrv8kcd2qs8cwgtdhgg6j4ux";

    private static Amt token(String labelPrefix, String name) {
        return Amt.builder().policyId(POLICY_ID)
                .unit(POLICY_ID + labelPrefix + HexFormat.of().formatHex(name.getBytes()))
                .assetName("asset1fingerprint" + name).build();
    }

    @Test
    public void testVirtualSubHandleResolvesToTheAddressInItsDatum() throws Exception {
        AddressUtxo addressUtxo = AddressUtxo.builder()
                .ownerAddr(HANDLE_SCRIPT)
                .amounts(List.of(token("00000000", "331@hal")))
                .inlineDatum(VirtualSubHandleDatumTest.readDatum(VirtualSubHandleDatumTest.DATUM_331_HAL))
                .slot(1400L)
                .build();

        List<AdaHandle> adaHandles = AdaHandleMapper.toAdaHandles(addressUtxo);

        assertThat(adaHandles, hasSize(1));
        assertThat(adaHandles.get(0).getName(), is("331@hal"));
        assertThat(adaHandles.get(0).getPaymentAddress(), is(VirtualSubHandleDatumTest.RESOLVED_331_HAL));
        assertThat(adaHandles.get(0).getStakeAddress(), is(VirtualSubHandleDatumTest.STAKE_331_HAL));
    }

    @Test
    public void testVirtualSubHandleWithoutDatumIsSkipped() {
        AddressUtxo addressUtxo = AddressUtxo.builder()
                .ownerAddr(HANDLE_SCRIPT)
                .amounts(List.of(token("00000000", "331@hal")))
                .slot(1400L)
                .build();

        // Never store the script holding the token as the owner.
        assertThat(AdaHandleMapper.toAdaHandles(addressUtxo), empty());
    }

    @Test
    public void testSubHandleSettingsTokenIsSkipped() {
        AddressUtxo addressUtxo = AddressUtxo.builder()
                .ownerAddr("addr1wypqchfrcwqg0tsqdcqeym962llsqg3g070xltlt3ydh0gq6h8gg4")
                .amounts(List.of(token("00001070", "komplett")))
                .slot(1400L)
                .build();

        // CIP-67 label 1: a root handle's sub-handle settings, held by a Handle script; not a handle.
        assertThat(AdaHandleMapper.toAdaHandles(addressUtxo), empty());
    }

    @Test
    public void testCip67Label() {
        assertThat(AdaHandleMapper.cip67Label(HexFormat.of().parseHex("000de14061")), is(222));
        assertThat(AdaHandleMapper.cip67Label(HexFormat.of().parseHex("000643b061")), is(100));
        assertThat(AdaHandleMapper.cip67Label(HexFormat.of().parseHex("0000107061")), is(1));
        assertThat(AdaHandleMapper.cip67Label(HexFormat.of().parseHex("0000000061")), is(0));
        // Legacy handle names and prefixes with a wrong checksum aren't labels
        assertThat(AdaHandleMapper.cip67Label("cardano".getBytes()), nullValue());
        assertThat(AdaHandleMapper.cip67Label(HexFormat.of().parseHex("000de15061")), nullValue());
        assertThat(AdaHandleMapper.cip67Label(new byte[]{0, 0}), nullValue());
    }

    @Test
    public void testToAdaHandlesIndexesDeMiHandles() {
        // demiking on mainnet, minted through Kora Labs' decentralized minting (DeMi) policy
        String policyId = "6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e";
        String hexName = HexFormat.of().formatHex("demiking".getBytes());

        AddressUtxo addressUtxo = AddressUtxo.builder()
                .ownerAddr("addr1vk4ua2cf830jqwa3s59dgasertugnu3598pmlwdk3uvpr2mv2xc3x6h7p")
                .ownerStakeAddr("stake1vk4ua2crberberbrtbdk3uvpr2mv2xc3x6h7p")
                .amounts(List.of(
                        Amt.builder().policyId(policyId).unit(policyId + "000de140" + hexName).assetName("asset1fingerprintuser").build(),
                        Amt.builder().policyId(policyId).unit(policyId + "000643b0" + hexName).assetName("asset1fingerprintref").build()))
                .slot(154639737L)
                .build();

        List<AdaHandle> adaHandles = AdaHandleMapper.toAdaHandles(addressUtxo);

        assertThat(adaHandles, hasSize(1));
        assertThat(adaHandles.get(0).getName(), is("demiking"));
    }
}

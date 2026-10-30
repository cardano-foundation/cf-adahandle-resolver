package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class VirtualSubHandleDatumTest {

    // Mainnet virtual sub-handle 331@hal; resolved address and holder as reported by api.handle.me.
    static final String DATUM_331_HAL = "datums/virtual-subhandle-331@hal.hex";
    static final String RESOLVED_331_HAL = "addr1qx4g62u00f6je722w60vhpnya898vlwuywfrqu4p0fgxp9m8vwutu6vpvh3qn96rf9ql258x9jcq3acvdfu9juzgd78suuhmc6";
    static final String STAKE_331_HAL = "stake1u9nk8w97dxqktcsfjap5js042rnzevqg7uxx57zewpyxlrcp2dj78";

    static String readDatum(String resource) throws IOException {
        return Files.readString(Path.of("src/test/resources", resource)).trim();
    }

    /** A CIP-68 datum, Constr 0 [metadata, 1, extra], whose extra resolves to the given address. */
    public static String cip68Datum(String resolvedAddress) {
        MapPlutusData ada = new MapPlutusData();
        ada.put(bytes("ada"), BytesPlutusData.of(new Address(resolvedAddress).getBytes()));
        MapPlutusData extra = new MapPlutusData();
        extra.put(bytes("resolved_addresses"), ada);
        return constr0(new MapPlutusData(), BigIntPlutusData.of(1), extra).serializeToHex();
    }

    static BytesPlutusData bytes(String value) {
        return BytesPlutusData.of(value.getBytes(StandardCharsets.UTF_8));
    }

    static ConstrPlutusData constr0(PlutusData... fields) {
        return ConstrPlutusData.builder().alternative(0)
                .data(ListPlutusData.builder().plutusDataList(List.of(fields)).build()).build();
    }

    @Test
    public void testOwnerFromMainnetDatum() throws IOException {
        var owner = VirtualSubHandleDatum.owner(readDatum(DATUM_331_HAL));

        assertThat(owner.isPresent(), is(true));
        assertThat(owner.get().paymentAddress(), is(RESOLVED_331_HAL));
        assertThat(owner.get().stakeAddress(), is(STAKE_331_HAL));
    }

    @Test
    public void testOwnerOfEnterpriseAddressHasNoStakeAddress() {
        String enterprise = "addr1vx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzers66hrl8";

        var owner = VirtualSubHandleDatum.owner(cip68Datum(enterprise));

        assertThat(owner.get().paymentAddress(), is(enterprise));
        assertThat(owner.get().stakeAddress(), nullValue());
    }

    @Test
    public void testMissingOrUnexpectedDatumHasNoOwner() {
        assertThat(VirtualSubHandleDatum.owner(null).isPresent(), is(false));
        assertThat(VirtualSubHandleDatum.owner("").isPresent(), is(false));
        assertThat(VirtualSubHandleDatum.owner("zz-not-hex").isPresent(), is(false));
        // Valid CIP-68 shape without resolved_addresses
        assertThat(VirtualSubHandleDatum.owner(
                constr0(new MapPlutusData(), BigIntPlutusData.of(1), new MapPlutusData()).serializeToHex()).isPresent(), is(false));
        // Not a constructor
        assertThat(VirtualSubHandleDatum.owner(BigIntPlutusData.of(42).serializeToHex()).isPresent(), is(false));
    }
}

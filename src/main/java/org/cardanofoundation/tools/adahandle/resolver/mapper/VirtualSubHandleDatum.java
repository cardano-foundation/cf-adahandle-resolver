package org.cardanofoundation.tools.adahandle.resolver.mapper;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.AddressType;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the address a virtual sub-handle resolves to from its token's inline datum.
 * <p>
 * A virtual sub-handle (CIP-67 label 0) is a token held by a Handle script; whoever holds it is not
 * the owner. Its datum follows CIP-68, {@code Constr 0 [metadata, version, extra]}, and the address
 * is {@code extra.resolved_addresses.ada} as raw address bytes (the same field api.handle.me uses).
 * <p>
 * Like api.handle.me, the address is encoded for the network being indexed, whatever network the
 * datum's address header claims; a stake address is only derived when the two agree.
 */
@Slf4j
public class VirtualSubHandleDatum {

    private static final byte[] RESOLVED_ADDRESSES = "resolved_addresses".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ADA = "ada".getBytes(StandardCharsets.UTF_8);

    public record Owner(String paymentAddress, String stakeAddress) {
    }

    /**
     * Returns the owner from the datum, or empty if the datum is missing or not in this format.
     *
     * @param mainnet whether the indexed chain is mainnet (addresses encoded as addr1/stake1)
     */
    public static Optional<Owner> owner(String inlineDatumHex, boolean mainnet) {
        if (inlineDatumHex == null || inlineDatumHex.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (!(PlutusData.deserialize(HexFormat.of().parseHex(inlineDatumHex)) instanceof ConstrPlutusData constr)
                    || constr.getAlternative() != 0) {
                return Optional.empty();
            }
            List<PlutusData> fields = constr.getData().getPlutusDataList();
            if (fields.size() < 3) {
                return Optional.empty();
            }
            return field(fields.get(2), RESOLVED_ADDRESSES)
                    .flatMap(resolvedAddresses -> field(resolvedAddresses, ADA))
                    .filter(BytesPlutusData.class::isInstance)
                    .map(ada -> toOwner(((BytesPlutusData) ada).getValue(), mainnet));
        } catch (Exception e) {
            log.warn("Unreadable virtual sub-handle datum: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Owner toOwner(byte[] addressBytes, boolean mainnet) {
        Address address = new Address(addressBytes);
        boolean sameNetwork = (addressBytes[0] & 0x0F) == (mainnet ? 1 : 0);
        String stakeAddress = sameNetwork && address.getAddressType() == AddressType.Base
                ? AddressProvider.getStakeAddress(address).toBech32()
                : null;
        return new Owner(new Address(mainnet ? "addr" : "addr_test", addressBytes).toBech32(), stakeAddress);
    }

    /** Looks up a bytes key in a Plutus map (datum map keys are UTF-8 bytes). */
    private static Optional<PlutusData> field(PlutusData data, byte[] key) {
        if (!(data instanceof MapPlutusData map)) {
            return Optional.empty();
        }
        for (Map.Entry<PlutusData, PlutusData> entry : map.getMap().entrySet()) {
            if (entry.getKey() instanceof BytesPlutusData bytes && Arrays.equals(bytes.getValue(), key)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}

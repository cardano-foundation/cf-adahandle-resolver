package org.cardanofoundation.tools.adahandle.resolver.mapper;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;

import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
public class AdaHandleMapper {
    private static final byte[] CIP68_100_PREFIX = {0, 6, 67, -80};
    private static final byte[] CIP68_222_PREFIX = {0, 13, -31, 64};

    public static ArrayList<AdaHandle> toAdaHandles(AddressUtxo addressUtxo) {
        final ArrayList<AdaHandle> adaHandles = new ArrayList<>();

        if (addressUtxo.getAmounts() != null) {
            for (final Amt amount : addressUtxo.getAmounts()) {
                String adaHandle = toHandleName(amount);
                if (adaHandle != null) {
                    adaHandles.add(new AdaHandle(adaHandle, addressUtxo.getOwnerStakeAddr(), addressUtxo.getOwnerAddr()));
                }
            }
        }
        return adaHandles;
    }

    /**
     * Returns the handle name carried by an amount, or null if it isn't an ADA Handle user token.
     * <p>
     * CIP-68 asset names start with a non-UTF-8 label, so yaci reports them as an asset fingerprint;
     * the name is decoded from the unit instead. CIP-68 reference tokens (label 100) hold metadata,
     * not ownership, and are skipped. Every table must key handles by this name, otherwise
     * rollbacks can't match history rows to current owners.
     */
    public static String toHandleName(Amt amount) {
        if (amount.getPolicyId() == null || !amount.getPolicyId().equals(AdaHandlePolicy.id())) {
            return null;
        }

        String adaHandle = amount.getAssetName();

        try {
            String assetNameFromUnit = amount.getUnit().replaceFirst("^" + amount.getPolicyId(), "");
            byte[] decodedAssetName = Hex.decodeHex(assetNameFromUnit);

            if (Arrays.equals(Arrays.copyOf(decodedAssetName, 4), CIP68_100_PREFIX)) {
                return null;
            }

            if (Arrays.equals(Arrays.copyOf(decodedAssetName, 4), CIP68_222_PREFIX)) {
                adaHandle = new String(Arrays.copyOfRange(decodedAssetName, 4, decodedAssetName.length));
            }
        } catch (Exception e) {
            log.warn("Error decoding asset name: {}", e.getMessage());
        }

        return adaHandle;
    }
}

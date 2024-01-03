package org.cardanofoundation.tools.adahandle.resolver.mapper;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.jpa.model.AddressUtxoEntity;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;

import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
public class AdaHandleMapper {
    public static ArrayList<AdaHandle> toAdaHandles(AddressUtxoEntity addressUtxoEntity) {
        final ArrayList<AdaHandle> adaHandles = new ArrayList<>();
        final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
        final byte[] CIP68_100_PREFIX = {0, 6, 67, -80};
        final byte[] CIP68_222_PREFIX = {0, 13, -31, 64};

        if (addressUtxoEntity.getAmounts() != null) {
            for (final Amt amount : addressUtxoEntity.getAmounts()) {
                if (amount.getPolicyId() != null) {
                    if (amount.getPolicyId().equals(ADA_HANDLE_POLICY_ID)) {
                        String adaHandle = amount.getAssetName();

                        try {
                            String assetNameFromUnit = amount.getUnit().replaceFirst("^" + amount.getPolicyId(), "");
                            byte[] decodedAssetName = Hex.decodeHex(assetNameFromUnit);

                            if(Arrays.equals(Arrays.copyOf(decodedAssetName, 4), CIP68_100_PREFIX)) {
                                continue;
                            }

                            if(Arrays.equals(Arrays.copyOf(decodedAssetName, 4), CIP68_222_PREFIX)) {
                                adaHandle = new String(Arrays.copyOfRange(decodedAssetName, 4, decodedAssetName.length));
                            }
                        } catch (DecoderException e) {
                            log.warn("Error decoding asset name: {}", e.getMessage());
                        }

                        adaHandles.add(new AdaHandle(adaHandle, addressUtxoEntity.getOwnerStakeAddr(), addressUtxoEntity.getOwnerAddr()));
                    }
                }
            }
        }
        return adaHandles;
    }
}

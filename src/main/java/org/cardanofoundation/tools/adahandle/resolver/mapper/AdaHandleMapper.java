package org.cardanofoundation.tools.adahandle.resolver.mapper;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.common.domain.Amt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
public class AdaHandleMapper {
    // CIP-67 labels of tokens that carry a handle's ownership (same as api.handle.me). Every other
    // label is skipped, e.g. 100 (CIP-68 reference token) and 1 (sub-handle settings token).
    private static final int LABEL_HANDLE = 222;
    private static final int LABEL_VIRTUAL_SUBHANDLE = 0;

    public static ArrayList<AdaHandle> toAdaHandles(AddressUtxo addressUtxo) {
        final ArrayList<AdaHandle> adaHandles = new ArrayList<>();

        if (addressUtxo.getAmounts() != null) {
            for (final Amt amount : addressUtxo.getAmounts()) {
                AdaHandle adaHandle = toAdaHandle(addressUtxo, amount);
                if (adaHandle != null) {
                    adaHandles.add(adaHandle);
                }
            }
        }
        return adaHandles;
    }

    /**
     * Returns the handle carried by an amount of this output, with the address it resolves to, or
     * null if the amount isn't a handle ownership token.
     * <p>
     * Names: CIP-68 asset names start with a non-UTF-8 label, so yaci reports them as an asset
     * fingerprint; the name is decoded from the unit instead. Owner: the output's address, except for
     * virtual sub-handles, whose token is held by a Handle script and whose address is in its datum.
     * <p>
     * ada_handle and the history table both use this, so a rollback restores exactly what was stored.
     */
    public static AdaHandle toAdaHandle(AddressUtxo addressUtxo, Amt amount) {
        if (amount.getPolicyId() == null || !amount.getPolicyId().equals(AdaHandlePolicy.id())) {
            return null;
        }

        byte[] assetName;
        try {
            assetName = Hex.decodeHex(amount.getUnit().replaceFirst("^" + amount.getPolicyId(), ""));
        } catch (Exception e) {
            log.warn("Error decoding asset name: {}", e.getMessage());
            return new AdaHandle(amount.getAssetName(), addressUtxo.getOwnerStakeAddr(), addressUtxo.getOwnerAddr());
        }

        Integer label = cip67Label(assetName);
        if (label == null) {
            // Legacy (CIP-25) handle: the asset name is the handle.
            return new AdaHandle(amount.getAssetName(), addressUtxo.getOwnerStakeAddr(), addressUtxo.getOwnerAddr());
        }

        String name = new String(Arrays.copyOfRange(assetName, 4, assetName.length), StandardCharsets.UTF_8);
        if (label == LABEL_HANDLE) {
            return new AdaHandle(name, addressUtxo.getOwnerStakeAddr(), addressUtxo.getOwnerAddr());
        }
        if (label == LABEL_VIRTUAL_SUBHANDLE) {
            // The network being indexed is the network of the Handle script holding the token.
            boolean mainnet = addressUtxo.getOwnerAddr() == null || !addressUtxo.getOwnerAddr().startsWith("addr_test");
            return VirtualSubHandleDatum.owner(addressUtxo.getInlineDatum(), mainnet)
                    .map(owner -> new AdaHandle(name, owner.stakeAddress(), owner.paymentAddress()))
                    .orElseGet(() -> {
                        log.warn("Skipping virtual sub-handle {} without a readable datum in {}#{}",
                                name, addressUtxo.getTxHash(), addressUtxo.getOutputIndex());
                        return null;
                    });
        }
        return null;
    }

    /**
     * Returns the CIP-67 label of an asset name, or null if it doesn't start with one. The 4-byte
     * prefix is {@code 0000 | 16-bit label | CRC-8 of the label | 0000}. Legacy handle names are
     * printable ASCII, so they can't start with a valid label.
     */
    static Integer cip67Label(byte[] assetName) {
        if (assetName.length < 4 || (assetName[0] & 0xF0) != 0 || (assetName[3] & 0x0F) != 0) {
            return null;
        }
        int prefix = ((assetName[0] & 0xFF) << 24) | ((assetName[1] & 0xFF) << 16)
                | ((assetName[2] & 0xFF) << 8) | (assetName[3] & 0xFF);
        int label = (prefix >>> 12) & 0xFFFF;
        int checksum = (prefix >>> 4) & 0xFF;
        return crc8(new byte[]{(byte) (label >>> 8), (byte) label}) == checksum ? label : null;
    }

    /** CRC-8 with polynomial 0x07, as used by CIP-67. */
    private static int crc8(byte[] data) {
        int crc = 0;
        for (byte b : data) {
            crc ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }
}

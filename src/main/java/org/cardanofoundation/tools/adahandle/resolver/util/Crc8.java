package org.cardanofoundation.tools.adahandle.resolver.util;

import lombok.experimental.UtilityClass;

/**
 * CRC-8 with polynomial 0x07, the checksum of CIP-67 asset name labels
 * (https://cips.cardano.org/cip/CIP-0067).
 *
 * @see org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandleMapper#cip67Label(byte[])
 */
@UtilityClass
public class Crc8 {

    public int crc8(byte[] data) {
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

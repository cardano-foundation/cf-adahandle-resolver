package org.cardanofoundation.tools.adahandle.resolver.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class Crc8Test {

    @Test
    public void testChecksumsOfCip67Labels() {
        // The checksum is the third and fourth hex digit of a label prefix: 000de140 for 222, 000643b0 for 100.
        assertThat(Crc8.crc8(new byte[]{0x00, (byte) 0xDE}), is(0x14));
        assertThat(Crc8.crc8(new byte[]{0x00, 0x64}), is(0x3b));
        assertThat(Crc8.crc8(new byte[]{0x00, 0x01}), is(0x07));
        assertThat(Crc8.crc8(new byte[]{0x00, 0x00}), is(0x00));
    }
}

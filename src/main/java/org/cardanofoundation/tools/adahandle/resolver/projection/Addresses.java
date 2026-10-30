package org.cardanofoundation.tools.adahandle.resolver.projection;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Addresses", description = "Addresses that currently hold an ADA Handle")
public interface Addresses {
    @Schema(description = "Bech32 stake address, or null if the holding address has no stake part", nullable = true)
    String getStakeAddress();

    @Schema(description = "Bech32 payment address that holds the handle")
    String getPaymentAddress();
}

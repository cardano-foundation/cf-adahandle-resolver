package org.cardanofoundation.tools.adahandle.resolver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ada-handles/")
@Tag(name = "ADA Handles", description = "Look up the ADA Handles held by an address")
public class AdaHandleController {

    @Autowired
    private AdaHandleService adaHandleService;
    @Autowired
    private AdaHandleHistoryService adaHandleHistoryService;

    @Operation(summary = "Handles held by a stake address",
            description = "Returns the names (without the leading $) of all handles held by addresses that " +
                    "belong to the given stake address. Returns an empty list if there are none.")
    @GetMapping("/by-stake-address/{stakeAddress}")
    public ResponseEntity<List<String>> getAdaHandlesByStakeAddress(
            @Parameter(description = "Bech32 stake address")
            @PathVariable String stakeAddress) {
        return ResponseEntity.ok(adaHandleService.getAdaHandlesByStakeAddress(stakeAddress));
    }

    @Operation(summary = "Handles held at a payment address",
            description = "Returns the names (without the leading $) of all handles held at exactly the given " +
                    "address. Returns an empty list if there are none.")
    @GetMapping("/by-payment-address/{paymentAddress}")
    public ResponseEntity<List<String>> getAdaHandlesByPaymentAddress(
            @Parameter(description = "Bech32 payment address")
            @PathVariable String paymentAddress) {
        return ResponseEntity.ok(adaHandleService.getAdaHandlesByPaymentAddress(paymentAddress));
    }
}

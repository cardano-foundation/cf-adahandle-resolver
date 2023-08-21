package org.cardanofoundation.tools.adahandle.resolver.controller;

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
public class AdaHandleController {

    @Autowired
    private AdaHandleService adaHandleService;
    @Autowired
    private AdaHandleHistoryService adaHandleHistoryService;

    @GetMapping("/by-stake-address/{stakeAddress}")
    public ResponseEntity<List<String>> getAdaHandlesByStakeAddress(
            @PathVariable String stakeAddress) {
        return ResponseEntity.ok(adaHandleService.getAdaHandlesByStakeAddress(stakeAddress));
    }

    @GetMapping("/by-payment-address/{paymentAddress}")
    public ResponseEntity<List<String>> getAdaHandlesByPaymentAddress(
            @PathVariable String paymentAddress) {
        return ResponseEntity.ok(adaHandleService.getAdaHandlesByPaymentAddress(paymentAddress));
    }
}

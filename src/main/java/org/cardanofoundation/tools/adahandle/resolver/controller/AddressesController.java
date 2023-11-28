package org.cardanofoundation.tools.adahandle.resolver.controller;

import org.cardanofoundation.tools.adahandle.resolver.projection.Addresses;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addresses/")
public class AddressesController {

    @Autowired
    private AdaHandleService adaHandleService;

    @GetMapping("/by-ada-handle/{adaHandle}")
    public ResponseEntity<Addresses> getAddressesByAdaHandle(
            @PathVariable String adaHandle) {
        return ResponseEntity.ok(adaHandleService.getAddressesByAdaHandle(adaHandle));
    }
}

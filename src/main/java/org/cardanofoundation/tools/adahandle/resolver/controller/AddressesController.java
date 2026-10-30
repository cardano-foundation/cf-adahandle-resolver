package org.cardanofoundation.tools.adahandle.resolver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Addresses", description = "Resolve an ADA Handle to the addresses that hold it")
public class AddressesController {

    @Autowired
    private AdaHandleService adaHandleService;

    @Operation(summary = "Resolve a handle to its addresses",
            description = "Returns the stake and payment address that currently hold the handle.")
    @ApiResponse(responseCode = "200", description = "Handle found")
    @ApiResponse(responseCode = "404", description = "Handle not found or not indexed yet", content = @Content)
    @GetMapping("/by-ada-handle/{adaHandle}")
    public ResponseEntity<Addresses> getAddressesByAdaHandle(
            @Parameter(description = "Handle name, with or without the leading $", example = "$alice")
            @PathVariable String adaHandle) {
        Addresses addresses = adaHandleService.getAddressesByAdaHandle(adaHandle);
        if (addresses == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(addresses);
    }
}

package org.cardanofoundation.tools.adahandle.resolver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.cardanofoundation.tools.adahandle.resolver.projection.Addresses;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addresses/")
@Tag(name = "Addresses", description = "Resolve an ADA Handle to the addresses that hold it")
@RequiredArgsConstructor
public class AddressesController {

    private final AdaHandleService adaHandleService;

    @Operation(summary = "Resolve a handle to its addresses",
            description = "Returns the payment address and, for a base address, the stake address the handle " +
                    "resolves to. Works for every kind of handle, under both ADA Handle policies (the original one " +
                    "and DeMi's): legacy and CIP-68 handles and NFT sub-handles resolve to the address holding the " +
                    "token; a virtual sub-handle resolves to the address in its datum " +
                    "(`extra.resolved_addresses.ada`), not to the script holding its token.")
    @ApiResponse(responseCode = "200", description = "Handle found")
    @ApiResponse(responseCode = "404", description = "Handle not found, burned, or not indexed yet", content = @Content)
    @GetMapping("/by-ada-handle/{adaHandle}")
    public ResponseEntity<Addresses> getAddressesByAdaHandle(
            @Parameter(description = "Handle name, with or without the leading $. A sub-handle, NFT or virtual, " +
                    "is written `sub@root`.", examples = {
                    @ExampleObject(name = "handle", value = "$alice"),
                    @ExampleObject(name = "sub-handle", value = "$sub@alice")})
            @PathVariable String adaHandle) {
        Addresses addresses = adaHandleService.getAddressesByAdaHandle(adaHandle);
        if (addresses == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(addresses);
    }
}

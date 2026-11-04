package org.cardanofoundation.tools.adahandle.resolver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adaHandleResolverOpenAPI(@Value("${app.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("ADA Handle Resolver API")
                        .version(version)
                        .description("Resolves ADA Handles to the addresses that hold them, and addresses back to " +
                                "handles. Data is indexed directly from the Cardano blockchain using Yaci Store.\n\n" +
                                "Every kind of ADA Handle is indexed, under both ADA Handle minting policies (the " +
                                "original one and DeMi's, decentralized minting): legacy (CIP-25) and CIP-68 " +
                                "handles, NFT sub-handles and virtual sub-handles (`sub@root`). A handle resolves to " +
                                "the address holding its token, except a virtual sub-handle, which resolves to the " +
                                "address in its datum. Burned handles are removed. The rules follow Kora Labs' " +
                                "implementation (api.handle.me).")
                        .contact(new Contact()
                                .name("Cardano Foundation")
                                .url("https://github.com/cardano-foundation/cf-adahandle-resolver"))
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/LICENSE")));
    }
}

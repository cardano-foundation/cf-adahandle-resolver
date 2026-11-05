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
                                "handles. Data is indexed directly from the Cardano blockchain using Yaci Store.")
                        .contact(new Contact()
                                .name("Cardano Foundation")
                                .url("https://github.com/cardano-foundation/cf-adahandle-resolver"))
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/cardano-foundation/cf-adahandle-resolver/blob/main/LICENSE")));
    }
}

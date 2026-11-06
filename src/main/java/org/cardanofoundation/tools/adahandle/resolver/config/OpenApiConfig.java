package org.cardanofoundation.tools.adahandle.resolver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.cardanofoundation.tools.adahandle.resolver.sync.SyncStatusHeaders;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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

    /**
     * Documents the X-Sync-* headers on every response of every API operation (#27).
     */
    @Bean
    public OpenApiCustomizer syncStatusHeadersCustomizer() {
        Map<String, Header> headers = Map.of(
                SyncStatusHeaders.STATUS, new Header()
                        .description("How current the data behind this answer is. synced: at the network tip and " +
                                "receiving blocks. syncing: catching up, so the answer can be out of date. stale: no " +
                                "block received from the node for a while, so the answer can be out of date. " +
                                "unknown: nothing indexed yet, or the network tip is not known.")
                        .schema(new StringSchema()._enum(List.of("synced", "syncing", "stale", "unknown"))),
                SyncStatusHeaders.SLOT, new Header()
                        .description("Slot of the last block indexed. Absent if nothing is indexed yet.")
                        .schema(new IntegerSchema().format("int64")),
                SyncStatusHeaders.BLOCK, new Header()
                        .description("Number of the last block indexed. Absent if nothing is indexed yet.")
                        .schema(new IntegerSchema().format("int64")),
                SyncStatusHeaders.BLOCK_TIME, new Header()
                        .description("Time of the last block indexed (ISO 8601, UTC). Compare it with the current " +
                                "time to see how old the data is.")
                        .schema(new StringSchema().format("date-time")),
                SyncStatusHeaders.TIP_SLOT, new Header()
                        .description("Slot of the network tip, as last fetched from the node (refreshed every few " +
                                "minutes). Absent if not known.")
                        .schema(new IntegerSchema().format("int64")));
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().values().forEach(response -> headers.forEach(response::addHeaderObject))));
    }
}

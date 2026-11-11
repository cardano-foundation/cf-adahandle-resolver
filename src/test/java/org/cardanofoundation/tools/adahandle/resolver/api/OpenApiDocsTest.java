package org.cardanofoundation.tools.adahandle.resolver.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"disable-indexer", "test"})
public class OpenApiDocsTest {

    @LocalServerPort
    private int serverPort;

    @BeforeAll
    public void setup() {
        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    public void testApiDocsExposesAllEndpoints() {
        when().get("/apidocs")
                .then()
                .statusCode(200)
                .body("openapi", startsWith("3."))
                .body("info.title", equalTo("ADA Handle Resolver API"))
                .body("paths.'/api/v1/addresses/by-ada-handle/{adaHandle}'.get", notNullValue())
                .body("paths.'/api/v1/ada-handles/by-stake-address/{stakeAddress}'.get", notNullValue())
                .body("paths.'/api/v1/ada-handles/by-payment-address/{paymentAddress}'.get", notNullValue());
    }

    @Test
    public void testApiDocsListOnlyResolverEndpoints() {
        // Yaci Store's own endpoints, such as /api/v1/sync/status, are served but not documented.
        when().get("/apidocs")
                .then()
                .statusCode(200)
                .body("paths.keySet()", containsInAnyOrder(
                        "/api/v1/addresses/by-ada-handle/{adaHandle}",
                        "/api/v1/ada-handles/by-stake-address/{stakeAddress}",
                        "/api/v1/ada-handles/by-payment-address/{paymentAddress}"));
    }

    @Test
    public void testYaciStorePluginApiIsOff() {
        // Yaci Store enables its plugin monitoring API unless store.plugins.api-enabled is false.
        when().get("/api/v1/admin/plugins")
                .then()
                .statusCode(404);
        when().get("/apidocs")
                .then()
                .statusCode(200)
                .body("paths.keySet().findAll { it.contains('/admin/') }", empty());
    }

    @Test
    public void testApiDocsDescribeTheSyncStatusHeaders() {
        String ok = "paths.'/api/v1/ada-handles/by-stake-address/{stakeAddress}'.get.responses.'200'.headers";
        String notFound = "paths.'/api/v1/addresses/by-ada-handle/{adaHandle}'.get.responses.'404'.headers";
        when().get("/apidocs")
                .then()
                .statusCode(200)
                .body(ok + ".'X-Sync-Status'.schema.enum", hasItems("synced", "syncing", "stale", "unknown"))
                .body(ok + ".'X-Sync-Slot'", notNullValue())
                .body(ok + ".'X-Sync-Block'", notNullValue())
                .body(ok + ".'X-Sync-Block-Time'.schema.format", equalTo("date-time"))
                .body(ok + ".'X-Sync-Tip-Slot'", notNullValue())
                .body(notFound + ".'X-Sync-Status'", notNullValue());
    }

    @Test
    public void testSwaggerUiIsServed() {
        when().get("/swagger-ui/index.html")
                .then()
                .statusCode(200);
    }
}

package org.cardanofoundation.tools.adahandle.resolver.api;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * The API is public and read-only: any site may call it from a browser, with GET only and without credentials.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"disable-indexer", "test"})
class CorsTest {

    private static final String ORIGIN = "https://some-dapp.example";
    private static final String HANDLE = "/api/v1/addresses/by-ada-handle/nobody";

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void setup() {
        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void anyOriginMayReadWithoutCredentials() {
        given().header("Origin", ORIGIN)
                .when().get(HANDLE)
                .then()
                .header("Access-Control-Allow-Origin", equalTo("*"))
                .header("Access-Control-Allow-Credentials", nullValue())
                .header("Access-Control-Expose-Headers", containsString("X-Sync-Status"));
    }

    @Test
    void preflightForGetIsAllowed() {
        given().header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "GET")
                .when().options(HANDLE)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("*"))
                .header("Access-Control-Allow-Methods", containsString("GET"))
                .header("Access-Control-Allow-Methods", not(containsString("DELETE")))
                .header("Access-Control-Allow-Credentials", nullValue());
    }

    @Test
    void preflightForWritesIsRefused() {
        for (String method : new String[]{"POST", "PUT", "DELETE", "PATCH"}) {
            given().header("Origin", ORIGIN)
                    .header("Access-Control-Request-Method", method)
                    .when().options(HANDLE)
                    .then()
                    .statusCode(403);
        }
    }
}

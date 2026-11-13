package org.cardanofoundation.tools.adahandle.resolver.api;

import io.restassured.RestAssured;
import org.cardanofoundation.tools.adahandle.resolver.sync.SyncState;
import org.cardanofoundation.tools.adahandle.resolver.sync.SyncStatusProvider;
import org.cardanofoundation.tools.adahandle.resolver.sync.SyncStatusSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

/**
 * Every API response says how current its data is (#27), in X-Sync-* headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"disable-indexer", "test"})
class SyncStatusHeadersTest {

    private static final String HANDLE = "/api/v1/addresses/by-ada-handle/nobody";
    private static final String BY_STAKE = "/api/v1/ada-handles/by-stake-address/stake1nobody";

    @MockitoBean
    private SyncStatusProvider syncStatusProvider;

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void setup() {
        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void syncedResponseCarriesAllHeaders() {
        when(syncStatusProvider.current()).thenReturn(new SyncStatusSnapshot(SyncState.SYNCED, 175_000_000L,
                12_000_000L, Instant.parse("2026-11-06T10:00:00Z"), 175_000_020L));

        given().get(BY_STAKE)
                .then()
                .statusCode(200)
                .header("X-Sync-Status", "synced")
                .header("X-Sync-Slot", "175000000")
                .header("X-Sync-Block", "12000000")
                .header("X-Sync-Block-Time", "2026-11-06T10:00:00Z")
                .header("X-Sync-Tip-Slot", "175000020");
    }

    @Test
    void notFoundCarriesTheHeadersToo() {
        // A 404 from an instance still syncing may only mean the handle isn't indexed yet.
        when(syncStatusProvider.current()).thenReturn(new SyncStatusSnapshot(SyncState.SYNCING, 60_000_000L,
                7_000_000L, Instant.parse("2022-04-01T00:00:00Z"), 175_000_020L));

        given().get(HANDLE)
                .then()
                .statusCode(404)
                .header("X-Sync-Status", "syncing")
                .header("X-Sync-Slot", "60000000");
    }

    @Test
    void staleIsReported() {
        when(syncStatusProvider.current()).thenReturn(new SyncStatusSnapshot(SyncState.STALE, 175_000_000L,
                12_000_000L, Instant.parse("2026-11-06T10:00:00Z"), 175_000_020L));

        given().get(BY_STAKE)
                .then()
                .header("X-Sync-Status", "stale");
    }

    @Test
    void unknownFieldsAreLeftOut() {
        when(syncStatusProvider.current()).thenReturn(SyncStatusSnapshot.UNKNOWN);

        given().get(BY_STAKE)
                .then()
                .statusCode(200)
                .header("X-Sync-Status", "unknown")
                .header("X-Sync-Slot", nullValue())
                .header("X-Sync-Block", nullValue())
                .header("X-Sync-Block-Time", nullValue())
                .header("X-Sync-Tip-Slot", nullValue());
    }

    @Test
    void onlyTheApiCarriesTheHeaders() {
        when(syncStatusProvider.current()).thenReturn(SyncStatusSnapshot.UNKNOWN);

        when().get("/actuator/health/liveness")
                .then()
                .header("X-Sync-Status", nullValue());
    }

    @Test
    void browsersMayReadTheHeaders() {
        when(syncStatusProvider.current()).thenReturn(SyncStatusSnapshot.UNKNOWN);

        given().header("Origin", "https://example.com")
                .get(BY_STAKE)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("*"))
                .header("Access-Control-Expose-Headers", allOf(containsString("X-Sync-Status"),
                        containsString("X-Sync-Slot"), containsString("X-Sync-Block"),
                        containsString("X-Sync-Block-Time"), containsString("X-Sync-Tip-Slot")));
    }
}

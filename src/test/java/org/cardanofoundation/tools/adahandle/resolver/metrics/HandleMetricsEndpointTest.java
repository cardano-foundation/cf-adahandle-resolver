package org.cardanofoundation.tools.adahandle.resolver.metrics;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

/** The handle metrics in /actuator/prometheus, under the names the docs and dashboards use. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Spring Boot tests switch metrics export off; this turns /actuator/prometheus back on.
@AutoConfigureObservability(tracing = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"disable-indexer", "test"})
class HandleMetricsEndpointTest {

    @LocalServerPort
    private int serverPort;

    @BeforeAll
    void setup() {
        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void prometheusServesTheHandleMetrics() {
        when().get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .body(allOf(
                        matchesPattern("(?s).*\\nadahandle_handles\\{[^}]*type=\"handle\"[^}]*} \\d+(\\.\\d+)?\\n.*"),
                        containsString("adahandle_handles{"),
                        containsString("adahandle_mints_total{"),
                        containsString("adahandle_burns_total{"),
                        containsString("adahandle_history_rows_total"),
                        containsString("adahandle_rollbacks_total{"),
                        containsString("adahandle_rollback_handles_total")));
    }

    @Test
    void onTheJvmTheHeapKeepsMicrometersPerPoolMetrics() {
        // MemoryMetrics adds id="heap" only where there are no heap pools (the native image).
        when().get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .body(allOf(
                        matchesPattern("(?s).*\\njvm_memory_used_bytes\\{[^}]*area=\"heap\"[^}]*} .*"),
                        not(containsString("id=\"heap\""))));
    }
}

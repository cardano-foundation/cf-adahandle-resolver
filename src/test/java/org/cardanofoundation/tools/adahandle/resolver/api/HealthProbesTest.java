package org.cardanofoundation.tools.adahandle.resolver.api;

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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;

/**
 * The health groups behind the Kubernetes probes and the docker compose healthcheck, with Yaci Store's nodeHealth and
 * nodeSync indicators in them. The indexer is off here, so those two report UNKNOWN; the groups are still UP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Spring Boot tests switch metrics export off; this turns /actuator/prometheus back on.
@AutoConfigureObservability(tracing = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles({"disable-indexer", "test"})
class HealthProbesTest {

    @LocalServerPort
    private int serverPort;

    @BeforeAll
    void setup() {
        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void healthShowsYaciStoreIndicators() {
        when().get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("components", hasKey("nodeHealth"))
                .body("components", hasKey("nodeSync"))
                .body("components", hasKey("db"))
                .body("groups", containsInAnyOrder("liveness", "readiness", "startup"));
    }

    @Test
    void livenessGroupChecksTheNodeConnection() {
        when().get("/actuator/health/liveness")
                .then()
                .statusCode(200)
                .body("components.keySet()", containsInAnyOrder("livenessState", "nodeHealth"));
    }

    @Test
    void readinessGroupWaitsForTheSync() {
        when().get("/actuator/health/readiness")
                .then()
                .statusCode(200)
                .body("components.keySet()", containsInAnyOrder("readinessState", "nodeSync", "db"));
    }

    @Test
    void startupGroupChecksDatabaseAndNode() {
        when().get("/actuator/health/startup")
                .then()
                .statusCode(200)
                .body("components.keySet()", containsInAnyOrder("db", "nodeHealth"));
    }

    @Test
    void prometheusMetricsCarryTheApplicationTag() {
        when().get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .body(containsString("application=\"cf-adahandle-resolver\""));
    }

    @Test
    void metricsEndpointIsExposed() {
        when().get("/actuator/metrics")
                .then()
                .statusCode(200)
                .body("names", hasItem("jvm.memory.used"));
    }
}

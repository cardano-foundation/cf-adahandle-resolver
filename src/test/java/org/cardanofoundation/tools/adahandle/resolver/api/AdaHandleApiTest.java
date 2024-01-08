package org.cardanofoundation.tools.adahandle.resolver.api;

import io.restassured.RestAssured;
import org.cardanofoundation.tools.adahandle.resolver.entity.AdaHandleHistoryItem;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleHistoryService;
import org.cardanofoundation.tools.adahandle.resolver.service.AdaHandleService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ComponentScan
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("[disable-indexer, test]")
public class AdaHandleApiTest {

    @Autowired
    AdaHandleService adaHandleService;

    @LocalServerPort
    private int serverPort;

    public final String ADA_HANDLE_ENDPOINT = "/api/v1/ada-handles";
    public final String ADDRESSES_ENDPOINT = "/api/v1/addresses";

    @BeforeAll
    public void setup() {
        List<AdaHandleHistoryItem> adaHandleHistoryItems = new ArrayList<>();
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(0L,"Max", "stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1000L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(1L,"Tom", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59ds35pe4jnhupmlwdk3uvpr2mv2xc3x6h7p", 1200L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(2L,"Otto", "stake1u87ua2crberberbrtbdk3uvpr2mv2xc3x6h7p","addr1u87ua2cf830jqwa3s59drvrt3gko5rvdrtf5pr2mv2xc3x6h7p", 1202L));
        adaHandleHistoryItems.add(new AdaHandleHistoryItem(3L,"Tom", "stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z","addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls", 1305L));
        adaHandleService.recalculateAdaHandlesFromHistory(adaHandleHistoryItems);

        RestAssured.port = serverPort;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    public void testAdaHandlesByStakeAddress() {
        when().get(ADA_HANDLE_ENDPOINT + "/by-stake-address/stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z")
                .then()
                .statusCode(200)
                .body(equalTo("[\"Max\",\"Tom\"]"));
    }

    @Test
    public void testAdaHandlesByPaymentAddress() {
        when().get(ADA_HANDLE_ENDPOINT + "/by-payment-address/addr1u87ua2cf830jqwa3s59drvrt3gko5rvdrtf5pr2mv2xc3x6h7p")
                .then()
                .statusCode(200)
                .body(equalTo("[\"Otto\"]"));
    }

    @Test
    public void testAddressesByAdaHandle() {
        when().get(ADDRESSES_ENDPOINT + "/by-ada-handle/Tom")
                .then()
                .statusCode(200)
                .body("stakeAddress", equalTo("stake1q8skl6ew6gu3gglq68n6dfv0p4hltwe3sh0z"))
                .body("paymentAddress", equalTo("addr1q8skl6ew6ghxrr7g0l2w5wsd6hg70wlm7u3gglq68n6dfv0p4hltws7gdl77ayrt3ls"));
    }
}

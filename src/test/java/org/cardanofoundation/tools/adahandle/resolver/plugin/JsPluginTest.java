package org.cardanofoundation.tools.adahandle.resolver.plugin;

import com.bloxbean.cardano.yaci.core.model.Amount;
import com.bloxbean.cardano.yaci.store.events.EventMetadata;
import com.bloxbean.cardano.yaci.store.events.MintBurnEvent;
import com.bloxbean.cardano.yaci.store.events.domain.TxMintBurn;
import com.bloxbean.cardano.yaci.store.plugin.cache.PluginStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * JavaScript plugins (ADR 12): the resolver ships GraalJS, so operators can write Yaci Store plugins in JavaScript.
 * The plugins are in application-js-plugins.yml; they report back through Yaci Store's global plugin state.
 */
@SpringBootTest(properties = "store.sync-auto-start=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "js-plugins"})
public class JsPluginTest {

    private static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
    private static final String OTHER_POLICY_ID = "00000000000000000000000000000000000000000000000000000000";

    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private PluginStateService pluginState;

    /** The scheduler's first run, at startup, is the first plugin call: wait for it before calling another plugin. */
    @BeforeEach
    public void waitForSchedulerFirstRun() {
        await().atMost(Duration.ofSeconds(30)).until(() -> pluginState.global().get("js-handle-count") != null);
    }

    @Test
    public void testJsEventHandlerSeesHandleMints() {
        pluginState.global().remove("js-last-handle-unit");

        publisher.publishEvent(mint(false, ADA_HANDLE_POLICY_ID, "616c696365"));
        publisher.publishEvent(mint(true, OTHER_POLICY_ID, "616c696365"));
        assertThat(pluginState.global().get("js-last-handle-unit"), nullValue());

        publisher.publishEvent(mint(true, ADA_HANDLE_POLICY_ID, "616c696365"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(pluginState.global().get("js-last-handle-unit"), equalTo(ADA_HANDLE_POLICY_ID + "616c696365")));
    }

    @Test
    public void testJsSchedulerQueriesResolverTables() {
        assertThat(((Number) pluginState.global().get("js-handle-count")).longValue(), equalTo(0L));
    }

    private static MintBurnEvent mint(boolean syncMode, String policyId, String assetNameHex) {
        Amount amount = Amount.builder()
                .policyId(policyId)
                .assetName(assetNameHex)
                .unit(policyId + assetNameHex)
                .quantity(BigInteger.ONE)
                .build();
        return MintBurnEvent.builder()
                .metadata(EventMetadata.builder().slot(1000L).syncMode(syncMode).build())
                .txMintBurns(List.of(TxMintBurn.builder().txHash("tx1000").amounts(List.of(amount)).build()))
                .build();
    }
}

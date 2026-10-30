package org.cardanofoundation.tools.adahandle.resolver.it;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A Yaci DevKit devnet and the resolver indexing it, for the integration tests.
 * <p>
 * Handles are minted under an always-true PlutusV2 policy, {@link #POLICY_ID}: the resolver under test runs with
 * {@code ADAHANDLE_POLICY_ID} set to it (see .github/workflows/integration-tests.yaml).
 */
@Slf4j
final class Devnet {

    static final String API_BASE_URL = env("API_BASE_URL", "http://localhost:9095");
    // Not YACI_STORE_URL: yaci-cli (Spring Boot) would read that as its yaci.store.url download URL.
    static final String YACI_STORE_URL = env("DEVNET_STORE_URL", "http://localhost:8080/api/v1/");
    static final String DEVKIT_ADMIN_URL = env("DEVKIT_ADMIN_URL", "http://localhost:10000");
    /** Part of every handle name, so that runs against the same devnet don't collide. */
    static final String RUN_ID = env("IT_RUN_ID", Long.toString(System.currentTimeMillis(), 36));

    // Devnet-only keys. HOLDER mints and holds the handles; RECEIVER gets the transferred ones.
    static final Account HOLDER = new Account(Networks.testnet(),
            "miss metal antenna jewel purity cause wear detail plastic engine idea clutch pony repair warfare indicate lizard dolphin various guide parent say space surprise");
    static final Account RECEIVER = new Account(Networks.testnet(),
            "shift half woman seven ketchup quick safe abstract record subway time wife cherry injury mad fresh advance autumn avocado response hard awake camera chunk");

    static final PlutusV2Script POLICY = PlutusV2Script.builder()
            .type("PlutusScriptV2")
            .cborHex("49480100002221200101")
            .build();
    static final String POLICY_ID = "3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712";
    /** The policy's script address, which holds the CIP-68 reference tokens. */
    static final String POLICY_ADDRESS = AddressProvider.getEntAddress(POLICY, Networks.testnet()).toBech32();

    static final Duration INDEXING_TIMEOUT = Duration.ofMinutes(3);

    private static final RestClient API = RestClient.create(API_BASE_URL);
    private static final RestClient HTTP = RestClient.create();
    private static final BackendService BACKEND = new BFBackendService(YACI_STORE_URL, "devnet");

    record Owner(String paymentAddress, String stakeAddress) {

        static Owner baseAddressOf(Account account) {
            return new Owner(account.baseAddress(), account.stakeAddress());
        }
    }

    private Devnet() {
    }

    static void waitForResolver() {
        log.info("Waiting for the resolver at {}", API_BASE_URL);
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
                .until(() -> API.get().uri("/actuator/health").retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful());
    }

    static void waitForYaciStore() {
        log.info("Waiting for Yaci Store at {}", YACI_STORE_URL);
        await().atMost(Duration.ofMinutes(5)).pollInterval(Duration.ofSeconds(3)).ignoreExceptions()
                .until(() -> HTTP.get().uri(YACI_STORE_URL + "epochs/latest/parameters").retrieve()
                        .toBodilessEntity().getStatusCode().is2xxSuccessful());
    }

    /** The owner the resolver returns for a handle, or empty if it doesn't know the handle. */
    static Optional<Owner> resolve(String handle) {
        return API.get().uri("/api/v1/addresses/by-ada-handle/{handle}", handle)
                .exchange((request, response) -> {
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Optional.empty();
                    }
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    return Optional.of(response.bodyTo(Owner.class));
                });
    }

    static void awaitOwner(String handle, Owner owner) {
        await("resolver to index " + handle).atMost(INDEXING_TIMEOUT).pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(resolve(handle)).contains(owner));
    }

    static List<String> handlesByPaymentAddress(String paymentAddress) {
        return API.get().uri("/api/v1/ada-handles/by-payment-address/{address}", paymentAddress)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    static List<String> handlesByStakeAddress(String stakeAddress) {
        return API.get().uri("/api/v1/ada-handles/by-stake-address/{address}", stakeAddress)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /**
     * Funds an account from the DevKit faucet and waits until the funds can be spent. The faucet sometimes accepts a
     * request whose transaction never shows up, so it is asked again a few times.
     */
    static void fund(Account account) {
        fund(account.baseAddress());
    }

    static void fund(String address) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HTTP.post().uri(DEVKIT_ADMIN_URL + "/local-cluster/api/addresses/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("address", address, "adaAmount", 10_000))
                        .retrieve().toBodilessEntity();
                await().atMost(Duration.ofSeconds(40)).pollInterval(Duration.ofSeconds(2)).ignoreExceptions()
                        .until(() -> isFunded(address));
                log.info("Funded {} (attempt {})", address, attempt);
                return;
            } catch (ConditionTimeoutException e) {
                log.warn("Funds of {} not available after 40 s (attempt {} of 3)", address, attempt);
            }
        }
        throw new IllegalStateException("the DevKit faucet did not fund " + address);
    }

    private static boolean isFunded(String address) throws Exception {
        Result<List<Utxo>> utxos = BACKEND.getUtxoService().getUtxos(address, 1, 1);
        return utxos.isSuccessful() && !utxos.getValue().isEmpty();
    }

    /** Submits a transaction paid and signed by the account, and waits until it is on chain. */
    static String submit(AbstractTx<?> tx, Account account) {
        Result<String> result = new QuickTxBuilder(BACKEND).compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(log::info);
        assertThat(result.isSuccessful()).as("transaction: %s", result.getResponse()).isTrue();
        return result.getValue();
    }

    private static String env(String name, String defaultValue) {
        return Optional.ofNullable(System.getenv(name)).filter(value -> !value.isBlank()).orElse(defaultValue);
    }
}

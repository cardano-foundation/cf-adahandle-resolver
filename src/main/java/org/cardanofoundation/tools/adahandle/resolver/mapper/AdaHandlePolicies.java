package org.cardanofoundation.tools.adahandle.resolver.mapper;

import java.util.Set;

/**
 * The minting policies whose tokens are indexed as handles: by default Kora Labs' two ADA Handle
 * policies, the same on mainnet, preprod and preview (koralabs/kora-labs-common,
 * src/handles/policies.ts). {@code adahandle.policy-ids} sets others (see AdaHandlePoliciesConfig);
 * only the integration tests do that, to index handles they mint on a local devnet.
 */
public final class AdaHandlePolicies {
    /** The original ADA Handle policy. */
    public static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
    /** Handles minted through Kora Labs' decentralized minting (DeMi), on mainnet since April 2025. */
    public static final String DEMI_POLICY_ID = "6c32db33a422e0bc2cb535bb850b5a6e9a9572222056d6ddc9cbc26e";
    public static final Set<String> DEFAULT = Set.of(ADA_HANDLE_POLICY_ID, DEMI_POLICY_ID);

    private static volatile Set<String> policyIds = DEFAULT;

    private AdaHandlePolicies() {
    }

    public static boolean contains(String policyId) {
        return policyId != null && policyIds.contains(policyId);
    }

    public static void set(Set<String> policyIds) {
        AdaHandlePolicies.policyIds = Set.copyOf(policyIds);
    }
}

package org.cardanofoundation.tools.adahandle.resolver.mapper;

/**
 * The minting policy whose tokens are indexed as handles: the ADA Handle policy, unless
 * {@code adahandle.policy-id} sets another one (see AdaHandlePolicyConfig). Only the integration
 * tests do that, to index handles they mint on a local devnet.
 */
public final class AdaHandlePolicy {
    public static final String ADA_HANDLE_POLICY_ID = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";

    private static volatile String policyId = ADA_HANDLE_POLICY_ID;

    private AdaHandlePolicy() {
    }

    public static String id() {
        return policyId;
    }

    public static void setId(String policyId) {
        AdaHandlePolicy.policyId = policyId;
    }
}

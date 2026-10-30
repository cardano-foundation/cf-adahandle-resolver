package org.cardanofoundation.tools.adahandle.resolver.config;

import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandlePolicies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AdaHandlePoliciesConfigTest {

    private static final String DEVNET_POLICY_ID = "3a888d65f16790950a72daee1f63aa05add6d268434107cfa5b67712";

    @AfterEach
    public void restoreDefault() {
        AdaHandlePolicies.set(AdaHandlePolicies.DEFAULT);
    }

    @Test
    public void testDefaultIndexesBothAdaHandlePolicies() {
        assertThat(AdaHandlePolicies.contains(AdaHandlePolicies.ADA_HANDLE_POLICY_ID), is(true));
        assertThat(AdaHandlePolicies.contains(AdaHandlePolicies.DEMI_POLICY_ID), is(true));
        assertThat(AdaHandlePolicies.contains(DEVNET_POLICY_ID), is(false));
        assertThat(AdaHandlePolicies.contains(null), is(false));
    }

    @Test
    public void testConfiguredPoliciesReplaceTheDefault() {
        new AdaHandlePoliciesConfig(new String[]{DEVNET_POLICY_ID, " " + AdaHandlePolicies.DEMI_POLICY_ID + " "});

        assertThat(AdaHandlePolicies.contains(DEVNET_POLICY_ID), is(true));
        assertThat(AdaHandlePolicies.contains(AdaHandlePolicies.DEMI_POLICY_ID), is(true));
        assertThat(AdaHandlePolicies.contains(AdaHandlePolicies.ADA_HANDLE_POLICY_ID), is(false));
    }

    @Test
    public void testRejectsWhatIsNotAPolicyId() {
        assertThrows(IllegalArgumentException.class, () -> new AdaHandlePoliciesConfig(new String[]{"$demiking"}));
        assertThrows(IllegalArgumentException.class, () -> new AdaHandlePoliciesConfig(new String[]{" "}));
    }
}

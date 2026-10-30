package org.cardanofoundation.tools.adahandle.resolver.config;

import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandlePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AdaHandlePolicyConfig {

    public AdaHandlePolicyConfig(@Value("${adahandle.policy-id}") String policyId) {
        if (!policyId.matches("[0-9a-f]{56}")) {
            throw new IllegalArgumentException("adahandle.policy-id must be a policy id (56 lowercase hex characters): " + policyId);
        }
        if (!policyId.equals(AdaHandlePolicy.ADA_HANDLE_POLICY_ID)) {
            log.warn("Indexing tokens of policy {} as handles instead of the ADA Handle policy", policyId);
        }
        AdaHandlePolicy.setId(policyId);
    }
}

package org.cardanofoundation.tools.adahandle.resolver.config;

import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.tools.adahandle.resolver.mapper.AdaHandlePolicies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class AdaHandlePoliciesConfig {

    public AdaHandlePoliciesConfig(@Value("${adahandle.policy-ids}") String[] configuredPolicyIds) {
        Set<String> policyIds = Arrays.stream(configuredPolicyIds).map(String::trim).filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());
        if (policyIds.isEmpty()) {
            throw new IllegalArgumentException("adahandle.policy-ids must name at least one policy");
        }
        for (String policyId : policyIds) {
            if (!policyId.matches("[0-9a-f]{56}")) {
                throw new IllegalArgumentException("adahandle.policy-ids must be policy ids (56 lowercase hex characters): " + policyId);
            }
        }
        if (!policyIds.equals(AdaHandlePolicies.DEFAULT)) {
            log.warn("Indexing tokens of policies {} as handles instead of the ADA Handle policies {}", policyIds,
                    AdaHandlePolicies.DEFAULT);
        }
        AdaHandlePolicies.set(policyIds);
    }
}

package com.kyro.platform.funding.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.funding.*}: pledge limits. Pledges are non-binding; no payment provider is involved. */
@ConfigurationProperties("kyro.funding")
public record FundingProperties(Long minPledgeMinor, List<String> currencies) {

    public FundingProperties {
        minPledgeMinor = minPledgeMinor == null ? 100L : minPledgeMinor;
        currencies = currencies == null || currencies.isEmpty() ? List.of("INR") : List.copyOf(currencies);
    }
}

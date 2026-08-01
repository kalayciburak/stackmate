package io.github.kalayciburak.stackmate.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionProfileGuardTest {

    @Test
    void blocksAnalysisAndRequestsOneWarningForAKnownActiveProductionProfile() {
        StackMateProperties properties = new StackMateProperties();
        MockEnvironment environment = new MockEnvironment().withProperty("ignored", "value");
        environment.setActiveProfiles("dev", "PROD");

        ProductionProfileGuard guard = new ProductionProfileGuard(environment, properties);

        assertThat(guard.decision().analysisAllowed()).isFalse();
        assertThat(guard.decision().warningRequired()).isTrue();
        assertThat(guard.decision().matchedProductionProfiles()).containsExactly("PROD");
        assertThat(guard.decision()).isSameAs(guard.decision());
    }

    @Test
    void permitsExplicitProductionUse() {
        StackMateProperties properties = new StackMateProperties();
        properties.getSafety().setAllowInProduction(true);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        ProductionProfileGuard.Decision decision =
                new ProductionProfileGuard(environment, properties).decision();

        assertThat(decision.analysisAllowed()).isTrue();
        assertThat(decision.warningRequired()).isFalse();
        assertThat(decision.matchedProductionProfiles()).containsExactly("production");
    }

    @Test
    void ignoresUnknownAndDefaultProfiles() {
        StackMateProperties properties = new StackMateProperties();
        properties.getSafety().setProductionProfiles(List.of("live"));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "dev");

        ProductionProfileGuard.Decision decision =
                new ProductionProfileGuard(environment, properties).decision();

        assertThat(decision.analysisAllowed()).isTrue();
        assertThat(decision.warningRequired()).isFalse();
        assertThat(decision.matchedProductionProfiles()).isEmpty();
    }
}

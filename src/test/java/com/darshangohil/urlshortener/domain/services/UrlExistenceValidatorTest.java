package com.darshangohil.urlshortener.domain.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

class UrlExistenceValidatorTest {

    private final UrlExistenceValidator validator = new UrlExistenceValidator(new DestinationGuard());

    @Test
    void unreachableHostIsRejected() {
        assertThat(validator.isUrlExists("https://example.invalid")).isFalse();
    }

    @Test
    void malformedUrlIsRejected() {
        assertThat(validator.isUrlExists("not a url")).isFalse();
    }

    // These hit the real network, so they stay opt-in: -Dnetwork.tests=true
    @Test
    @EnabledIfSystemProperty(named = "network.tests", matches = "true")
    void botBlockingSiteIsAccepted() {
        // LinkedIn rejects HEAD with 405 and answers GET with a non-standard 999
        assertThat(validator.isUrlExists("https://www.linkedin.com/in/da-gohil/")).isTrue();
    }

    @Test
    @EnabledIfSystemProperty(named = "network.tests", matches = "true")
    void ordinarySiteIsAccepted() {
        assertThat(validator.isUrlExists("https://example.com")).isTrue();
    }

    @Test
    @EnabledIfSystemProperty(named = "network.tests", matches = "true")
    void missingPageOnRealHostIsRejected() {
        assertThat(validator.isUrlExists("https://example.com/no-such-page-here-12345")).isFalse();
    }
}

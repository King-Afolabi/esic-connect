package com.esic.connect.shared.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les clés de limitation ne doivent jamais contenir une adresse
 * électronique ou une adresse IP en clair (RG-094, docs/02 §23.3).
 */
class IdentityHashingTests {

    @Test
    void theRawValueNeverAppearsInTheHash() {
        String email = "alice.martin@esic-connect.test";
        String hash = IdentityHashing.of(email);

        assertThat(hash).doesNotContain("alice", "martin", "esic-connect", "@");
    }

    @Test
    void theHashIsStableAndFixedLength() {
        assertThat(IdentityHashing.of("alice@esic-connect.test"))
                .isEqualTo(IdentityHashing.of("alice@esic-connect.test"))
                .hasSize(32);
    }

    @Test
    void caseAndSurroundingSpacesDoNotProduceDifferentBuckets() {
        assertThat(IdentityHashing.of("  Alice@ESIC-Connect.test "))
                .isEqualTo(IdentityHashing.of("alice@esic-connect.test"));
    }

    @Test
    void differentValuesProduceDifferentBuckets() {
        assertThat(IdentityHashing.of("alice@esic-connect.test"))
                .isNotEqualTo(IdentityHashing.of("bob@esic-connect.test"));
    }

    @Test
    void anAbsentIdentityFallsBackToASharedAnonymousBucket() {
        assertThat(IdentityHashing.of(null)).isEqualTo("anonymous");
        assertThat(IdentityHashing.of("   ")).isEqualTo("anonymous");
    }
}

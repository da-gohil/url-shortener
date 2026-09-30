package com.darshangohil.urlshortener.domain.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerFilterTest {

    @Test
    void parsesGuestsAndUserIds() {
        assertThat(OwnerFilter.parse("guest")).isEqualTo(OwnerFilter.GUESTS);
        assertThat(OwnerFilter.parse(" GUEST ")).isEqualTo(OwnerFilter.GUESTS);
        assertThat(OwnerFilter.parse("42")).isEqualTo(OwnerFilter.user(42L));
    }

    @Test
    void blankOrGarbageMeansAnyone() {
        assertThat(OwnerFilter.parse(null)).isEqualTo(OwnerFilter.ANYONE);
        assertThat(OwnerFilter.parse(" ")).isEqualTo(OwnerFilter.ANYONE);
        assertThat(OwnerFilter.parse("bob")).isEqualTo(OwnerFilter.ANYONE);
    }

    @Test
    void toParamRoundTrips() {
        for (OwnerFilter owner : new OwnerFilter[]{OwnerFilter.ANYONE, OwnerFilter.GUESTS, OwnerFilter.user(7L)}) {
            assertThat(OwnerFilter.parse(owner.toParam())).isEqualTo(owner);
        }
    }
}

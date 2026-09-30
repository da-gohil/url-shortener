package com.darshangohil.urlshortener.web.dtos;

import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd;
import com.darshangohil.urlshortener.domain.models.UpdateShortUrlCmd.Expiry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditShortUrlFormTest {

    @Test
    void expiryChoiceIsCaseInsensitiveAndDefaultsToKeep() {
        assertThat(new EditShortUrlForm(false, "NEVER", null).expiryChoice()).isEqualTo(Expiry.NEVER);
        assertThat(new EditShortUrlForm(false, " days ", 3).expiryChoice()).isEqualTo(Expiry.DAYS);
        assertThat(new EditShortUrlForm(false, null, null).expiryChoice()).isEqualTo(Expiry.KEEP);
        assertThat(new EditShortUrlForm(false, "tomorrow", null).expiryChoice()).isEqualTo(Expiry.KEEP);
    }

    @Test
    void onlyTheDaysChoiceNeedsANumber() {
        assertThat(new EditShortUrlForm(false, "days", null).isMissingDays()).isTrue();
        assertThat(new EditShortUrlForm(false, "days", 7).isMissingDays()).isFalse();
        assertThat(new EditShortUrlForm(false, "never", null).isMissingDays()).isFalse();
    }

    @Test
    void anUntickedCheckboxMeansPublic() {
        assertThat(new EditShortUrlForm(null, "keep", null).toCmd())
                .isEqualTo(new UpdateShortUrlCmd(false, Expiry.KEEP, null));
        assertThat(new EditShortUrlForm(true, "days", 30).toCmd())
                .isEqualTo(new UpdateShortUrlCmd(true, Expiry.DAYS, 30));
    }
}

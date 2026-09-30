package com.darshangohil.urlshortener.domain.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void aReasonablePasswordHasNoProblems() {
        assertThat(policy.problems("correct horse battery staple", "jane.smith@example.com", "Jane Smith")).isEmpty();
    }

    @Test
    void morethan72BytesIsRefusedEvenWhenItIsFewerThan72Characters() {
        String emoji = "😀".repeat(30);   // 60 Java chars, 120 bytes

        assertThat(policy.problems(emoji, "a@example.com", "A")).singleElement()
                .asString().contains("under 72 bytes");
        assertThat(policy.problems("x".repeat(72), "a@example.com", "A")).isEmpty();
    }

    @Test
    void commonPasswordsAreRefusedWhateverTheCase() {
        assertThat(policy.problems("Password123", "a@example.com", "A"))
                .containsExactly("That password is too common. Pick something harder to guess.");
        assertThat(policy.problems("QWERTYUIOP", "a@example.com", "A")).hasSize(1);
    }

    @Test
    void theEmailOrANameInThePasswordIsRefused() {
        assertThat(policy.problems("jane.smith-rules!", "jane.smith@example.com", "Someone"))
                .containsExactly("Don't use your name or email address in your password.");
        assertThat(policy.problems("i-am-SMITH-2026", "x@example.com", "Jane Smith")).hasSize(1);
    }

    @Test
    void veryShortNamePartsDoNotCount() {
        // "Al" is too short to be meaningful, and would flag half the dictionary
        assertThat(policy.problems("totally-alright-pass", "al@example.com", "Al Li")).isEmpty();
    }

    @Test
    void severalProblemsAreAllReported() {
        assertThat(policy.problems("password1", "password1@example.com", "X")).hasSize(2);
    }
}

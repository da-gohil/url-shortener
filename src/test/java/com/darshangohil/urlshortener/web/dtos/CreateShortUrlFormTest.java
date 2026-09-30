package com.darshangohil.urlshortener.web.dtos;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateShortUrlFormTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "www.facebook.com,            https://www.facebook.com",
            "facebook.com,                https://facebook.com",
            "'  www.facebook.com  ',      https://www.facebook.com",
            "http://music.com,            http://music.com",
            "https://music.com,           https://music.com",
            "HTTP://music.com,            HTTP://music.com"
    })
    void acceptsAndNormalizes(String input, String stored) {
        var form = new CreateShortUrlForm(input);
        assertThat(form.originalUrl()).isEqualTo(stored);
        assertThat(validator.validate(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://music.com", "/admin"})
    void rejectsNonHttpSchemes(String input) {
        assertThat(validator.validate(new CreateShortUrlForm(input)))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Enter a valid http(s) URL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankReportsOnlyTheRequiredMessage(String input) {
        assertThat(validator.validate(new CreateShortUrlForm(input)))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Original URL is required"));
    }

    @Test
    void noArgConstructorIsBlank() {
        assertThat(new CreateShortUrlForm().originalUrl()).isEmpty();
    }
}

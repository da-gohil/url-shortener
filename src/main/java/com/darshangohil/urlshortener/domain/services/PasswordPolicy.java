package com.darshangohil.urlshortener.domain.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Password rules beyond the form's length check. Returns every problem, worded for the
 * person choosing the password.
 */
@Component
public class PasswordPolicy {

    /** BCrypt only uses the first 72 bytes, and Spring Security refuses longer input outright. */
    static final int MAX_BYTES = 72;
    private static final int MIN_PERSONAL_PART = 3;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this.commonPasswords = load("common-passwords.txt");
    }

    public List<String> problems(String password, String email, String name) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            return problems;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("That password is too long: keep it under 72 bytes. Emoji and accented "
                    + "letters take more than one byte each.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lower)) {
            problems.add("That password is too common. Pick something harder to guess.");
        }
        if (containsPersonalPart(lower, email, name)) {
            problems.add("Don't use your name or email address in your password.");
        }
        return problems;
    }

    /** The email's local part, or any word of the name, of at least 3 letters. */
    private static boolean containsPersonalPart(String lowerPassword, String email, String name) {
        List<String> parts = new ArrayList<>();
        if (email != null && email.contains("@")) {
            parts.add(email.substring(0, email.indexOf('@')));
        }
        if (name != null) {
            parts.addAll(Arrays.asList(name.split("\\s+")));
        }
        return parts.stream()
                .map(part -> part.strip().toLowerCase(Locale.ROOT))
                .filter(part -> part.length() >= MIN_PERSONAL_PART)
                .anyMatch(lowerPassword::contains);
    }

    private static Set<String> load(String resource) {
        try (var in = new ClassPathResource(resource).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }
}

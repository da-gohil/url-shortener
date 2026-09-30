package com.darshangohil.urlshortener.domain.models;

public record CreateUserCmd(
        String name,
        String email,
        String password
) {
}

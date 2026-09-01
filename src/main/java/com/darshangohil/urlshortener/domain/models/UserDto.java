package com.darshangohil.urlshortener.domain.models;

import java.io.Serializable;

/**
 * DTO for {@link com.darshangohil.urlshortener.domain.entities.User}
 */
public record UserDto(Long id, String name) implements Serializable {
}
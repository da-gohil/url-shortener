package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // emails are stored folded to lower case, but people type them however they like
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

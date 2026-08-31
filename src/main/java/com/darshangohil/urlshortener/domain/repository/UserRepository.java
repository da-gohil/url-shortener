package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
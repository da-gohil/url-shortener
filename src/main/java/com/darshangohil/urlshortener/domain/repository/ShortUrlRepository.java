package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    // Custom JPQL Query
    @Query("SELECT su FROM ShortUrl su left join fetch su.createdBy where su.isPrivate = false order by su.createdAt desc")
    List<ShortUrl> findPublicShortUrl();

    boolean existsByShortKey(String shortKey);

    Optional<ShortUrl> findByShortKey(String shortKey);
}
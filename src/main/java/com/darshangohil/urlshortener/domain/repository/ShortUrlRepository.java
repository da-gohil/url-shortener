package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    // Derived Query Method (Requires createdAt in ShortUrl entity)
    List<ShortUrl> findByIsPrivateIsFalseOrderByCreatedAtDesc();

    // Custom JPQL Query (Fixed 'FROM ShortUrl su' alias)
    @Query("SELECT su FROM ShortUrl su WHERE su.isPrivate = false order by su.createdAt desc")
    List<ShortUrl> findPublicShortUrl();
}
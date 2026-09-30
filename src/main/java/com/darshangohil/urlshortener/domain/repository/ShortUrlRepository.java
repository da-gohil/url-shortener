package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    // @EntityGraph rather than "join fetch": a fetch join with a Pageable forces
    // Hibernate to paginate in memory (HHH000104), which defeats the point.
    @EntityGraph(attributePaths = "createdBy")
    @Query("SELECT su FROM ShortUrl su where su.isPrivate = false")
    Page<ShortUrl> findPublicShortUrls(Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    Page<ShortUrl> findByCreatedById(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query("SELECT su FROM ShortUrl su")
    Page<ShortUrl> findAllShortUrls(Pageable pageable);

    boolean existsByShortKey(String shortKey);

    Optional<ShortUrl> findByShortKey(String shortKey);

    List<ShortUrl> findAllByIdIn(List<Long> ids);
}

package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    // @EntityGraph rather than "join fetch": a fetch join with a Pageable forces
    // Hibernate to paginate in memory (HHH000104), which defeats the point.
    /** Public links that have not expired as of {@code now}: the home page listing. */
    @EntityGraph(attributePaths = "createdBy")
    @Query("""
            SELECT su FROM ShortUrl su
            WHERE su.isPrivate = false
              AND (su.expiresAt IS NULL OR su.expiresAt > :now)
            """)
    Page<ShortUrl> findActivePublicShortUrls(Instant now, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    Page<ShortUrl> findByCreatedById(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    @Query("SELECT su FROM ShortUrl su")
    Page<ShortUrl> findAllShortUrls(Pageable pageable);

    boolean existsByShortKey(String shortKey);

    Optional<ShortUrl> findByShortKey(String shortKey);

    /**
     * Adds one click in a single UPDATE, so concurrent visits cannot overwrite each
     * other the way a read-increment-save would. {@code @Transactional} so it also
     * works outside a service transaction, as Spring Data requires for writes.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ShortUrl su SET su.clickCount = su.clickCount + 1 WHERE su.id = :id")
    void incrementClickCount(Long id);

    List<ShortUrl> findAllByIdIn(List<Long> ids);
}

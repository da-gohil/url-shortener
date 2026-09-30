package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.ShortUrl;
import com.darshangohil.urlshortener.domain.models.UserUrlStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long>,
        JpaSpecificationExecutor<ShortUrl> {

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

    /** Redeclared only to add the entity graph; see ShortUrlSpecifications. */
    @Override
    @EntityGraph(attributePaths = "createdBy")
    Page<ShortUrl> findAll(Specification<ShortUrl> spec, Pageable pageable);

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

    /** One aggregate query rather than loading the user's links to count them. */
    @Query("""
            SELECT new com.darshangohil.urlshortener.domain.models.UserUrlStats(
                COUNT(su),
                COALESCE(SUM(su.clickCount), 0L),
                COALESCE(SUM(CASE WHEN su.expiresAt IS NULL OR su.expiresAt > :now
                                  THEN 1L ELSE 0L END), 0L))
            FROM ShortUrl su
            WHERE su.createdBy.id = :userId
            """)
    UserUrlStats getUserStats(Long userId, Instant now);
}

package com.darshangohil.urlshortener.domain.repository;

import com.darshangohil.urlshortener.domain.entities.User;
import com.darshangohil.urlshortener.domain.models.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // emails are stored folded to lower case, but people type them however they like
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Users with how many links each has, for the admin Users tab. {@code query}
     * matches name or email (case-insensitive); pass {@code ""} for everyone. (Not
     * {@code null}: PostgreSQL can't type an untyped null inside LOWER().)
     */
    @Query(value = """
            SELECT new com.darshangohil.urlshortener.domain.models.UserSummary(
                u.id, u.name, u.email, u.role, u.enabled, u.createdAt, COUNT(su))
            FROM User u LEFT JOIN u.shortUrls su
            WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
            GROUP BY u.id, u.name, u.email, u.role, u.enabled, u.createdAt
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    Page<UserSummary> findUserSummaries(String query, Pageable pageable);
}

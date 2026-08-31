-- Insert Seed Users
INSERT INTO users (id, email, password, name, role)
VALUES (1, 'admin@example.com', '$2a$10$e7x8K9mL2vP1qR0sT3uV4wX5yZ6a7b8c9d0e1f2g3h4i5j6k7l8m', 'Admin User',
        'ROLE_ADMIN'),
       (2, 'john.doe@example.com', '$2a$10$a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z', 'John Doe',
        'ROLE_USER'),
       (3, 'jane.smith@example.com', '$2a$10$z9y8x7w6v5u4t3s2r1q0p9o8n7m6l5k4j3i2h1g0f9e8d7c6b5a', 'Jane Smith',
        'ROLE_USER'),
       (4, 'dagohil@proton.me', 'test', 'Danny Gohil', 'ROLE_USER');

-- Insert Seed Short URLs
INSERT INTO short_urls (id, short_key, original_url, is_private, expires_at, created_by, click_count)
VALUES (1, 'aB3xZ9', 'https://spring.io/projects/spring-boot', false, NULL, 1, 142),
       (2, 'k9M2pQ', 'https://developer.mozilla.org/en-US/docs/Web/PostgreSQL', false, NULL, 2, 28),
       (3, 'x7W1vN', 'https://github.com/trending', false, '2026-12-31 23:59:59', 3, 5),
       (4, 'p0L8kJ', 'https://docs.oracle.com/en/java/javase/21/', true, NULL, 2, 0),
       (5, 'm4N1bV', 'https://postgresql.org/docs/current/index.html', false, NULL, NULL, 89);

-- Reset PostgreSQL BIGSERIAL sequences so future JPA saves generate IDs starting after 4 and 5
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('short_urls_id_seq', (SELECT MAX(id) FROM short_urls));
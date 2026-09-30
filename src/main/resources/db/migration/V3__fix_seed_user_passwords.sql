-- The password column in V2 held placeholder text, not real BCrypt output, so none of
-- the seed accounts could actually sign in. V2 is already applied and checksummed, so
-- the fix lands here instead of being edited into it.
--
-- Every seed account below now has the BCrypt hash of "password".
-- LOCAL DEVELOPMENT SEED DATA ONLY -- do not ship this migration to a real environment.
UPDATE users
SET password = '$2a$10$hgUA5YYuuHg7rto/1OTufuJVK8ZMxVgTpAKliJOt.tJ.OGe0prwES'
WHERE email IN ('admin@example.com',
                'john.doe@example.com',
                'jane.smith@example.com',
                'dagohil@proton.me');

-- An admin can disable a link: it stops redirecting but stays on record (and its
-- short key stays taken), unlike a delete.
ALTER TABLE short_urls ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;

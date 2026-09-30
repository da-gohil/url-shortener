-- An admin can disable an account: its owner can no longer sign in. Their links are
-- left alone; disable those separately if they need to stop redirecting.
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

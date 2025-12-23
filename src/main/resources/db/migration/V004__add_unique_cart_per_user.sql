-- ============================================================
-- Enforce single cart per user to avoid duplicates under concurrency
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_carts_user_id
ON carts(user_id);

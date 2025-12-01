-- ============================================================
-- Enforce uniqueness for cart item per (cart_id, product_id)
-- and reduce gap-lock contention on inserts
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_cart_items_cart_product
ON cart_items(cart_id, product_id);


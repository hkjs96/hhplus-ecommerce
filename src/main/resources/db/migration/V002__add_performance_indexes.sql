-- ============================================================
-- Performance Optimization Indexes
-- STEP 08: Database Optimization
-- Created: 2025-01-13
-- ============================================================

-- ============================================================
-- 1. 인기 상품 조회 최적화
-- ============================================================

-- orders 테이블: status + paid_at 복합 인덱스 (Covering Index)
-- 용도: WHERE o.status = 'COMPLETED' AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
CREATE INDEX IF NOT EXISTS idx_status_paid_at
ON orders(status, paid_at);

-- order_items 테이블: Covering Index (JOIN + 집계 컬럼 모두 포함)
-- 용도: SELECT oi.product_id, COUNT(*), SUM(oi.subtotal) FROM order_items oi WHERE oi.order_id IN (...)
CREATE INDEX IF NOT EXISTS idx_order_product_covering
ON order_items(order_id, product_id, quantity, subtotal);

-- ============================================================
-- 2. 장바구니 조회 최적화
-- ============================================================

-- carts 테이블: user_id 인덱스
-- 용도: WHERE c.user_id = ?
CREATE INDEX IF NOT EXISTS idx_carts_user_id
ON carts(user_id);

-- cart_items 테이블: cart_id 인덱스
-- 용도: WHERE ci.cart_id = ?
CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id
ON cart_items(cart_id);

-- cart_items 테이블: product_id 인덱스 (JOIN 최적화)
-- 용도: JOIN products p ON ci.product_id = p.id
CREATE INDEX IF NOT EXISTS idx_cart_items_product_id
ON cart_items(product_id);

-- ============================================================
-- 3. 쿠폰 조회 최적화
-- ============================================================

-- user_coupons 테이블: user_id + status 복합 인덱스
-- 용도: WHERE uc.user_id = ? AND uc.status = ?
CREATE INDEX IF NOT EXISTS idx_user_coupons_user_status
ON user_coupons(user_id, status);

-- user_coupons 테이블: coupon_id 인덱스 (JOIN 최적화)
-- 용도: JOIN coupons c ON uc.coupon_id = c.id
CREATE INDEX IF NOT EXISTS idx_user_coupons_coupon_id
ON user_coupons(coupon_id);

-- coupons 테이블: expires_at 인덱스
-- 용도: WHERE c.expires_at > NOW()
CREATE INDEX IF NOT EXISTS idx_coupons_expires_at
ON coupons(expires_at);

-- ============================================================
-- 4. 상품 검색 최적화
-- ============================================================

-- products 테이블: category + stock + created_at 복합 인덱스
-- 용도: WHERE p.category = ? AND p.stock > 0 ORDER BY p.created_at DESC
CREATE INDEX IF NOT EXISTS idx_products_category_stock_created
ON products(category, stock, created_at);

-- ============================================================
-- 인덱스 사용 분석 쿼리 (모니터링용)
-- ============================================================

-- MySQL에서 인덱스 사용률 확인:
-- SHOW INDEX FROM orders;
-- SHOW INDEX FROM order_items;
-- SHOW INDEX FROM carts;
-- SHOW INDEX FROM cart_items;
-- SHOW INDEX FROM user_coupons;
-- SHOW INDEX FROM coupons;
-- SHOW INDEX FROM products;

-- 인덱스 크기 확인:
-- SELECT
--     table_name,
--     index_name,
--     ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
-- FROM mysql.innodb_index_stats
-- WHERE stat_name = 'size'
--   AND table_name IN ('orders', 'order_items', 'carts', 'cart_items', 'user_coupons', 'coupons', 'products')
-- ORDER BY table_name, index_name;

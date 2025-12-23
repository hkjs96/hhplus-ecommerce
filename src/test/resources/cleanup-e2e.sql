-- E2E 테스트 후 정리
-- 테스트 데이터 삭제

DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = 999);
DELETE FROM orders WHERE user_id = 999;
DELETE FROM user_coupons WHERE user_id = 999;
DELETE FROM users WHERE id = 999;
DELETE FROM products WHERE id = 888;

-- 멱등성 키 정리
DELETE FROM order_idempotency WHERE idempotency_key LIKE 'E2E-ORDER-%';
DELETE FROM payment_idempotency WHERE idempotency_key LIKE 'E2E-PAYMENT-%';

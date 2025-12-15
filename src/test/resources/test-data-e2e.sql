-- E2E 테스트용 고정 데이터
-- 사용자 ID: 999, 상품 ID: 888

-- 사용자 데이터 (잔액 100만원)
INSERT INTO users (id, email, username, balance, created_at, updated_at)
VALUES (999, 'e2e-test@example.com', 'E2E테스트유저', 1000000, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    email = 'e2e-test@example.com',
    username = 'E2E테스트유저',
    balance = 1000000,
    updated_at = NOW();

-- 상품 데이터 (재고 100개, 가격 10,000원)
INSERT INTO products (id, code, name, description, price, category, stock, created_at, updated_at)
VALUES (888, 'E2E-P001', 'E2E테스트상품', 'E2E 테스트용 상품', 10000, '전자제품', 100, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    code = 'E2E-P001',
    name = 'E2E테스트상품',
    description = 'E2E 테스트용 상품',
    price = 10000,
    category = '전자제품',
    stock = 100,
    updated_at = NOW();

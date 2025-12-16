-- E2E 테스트용 고정 데이터
-- 사용자 ID: 999, 상품 ID: 888

-- 사용자 데이터 (잔액 100만원)
INSERT INTO users (id, email, username, balance, version, created_at, updated_at)
VALUES (999, 'e2e-test@example.com', 'E2E테스트유저', 1000000, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    email = 'e2e-test@example.com',
    username = 'E2E테스트유저',
    balance = 1000000,
    version = 0,
    updated_at = NOW();

-- 상품 데이터 (재고 넉넉히 설정: 고액 주문 시 생성 성공, 결제 단계에서 잔액 부족 검증)
INSERT INTO products (id, product_code, name, description, price, category, stock, version, created_at, updated_at)
VALUES (888, 'E2E-P001', 'E2E테스트상품', 'E2E 테스트용 상품', 10000, '전자제품', 500, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    product_code = 'E2E-P001',
    name = 'E2E테스트상품',
    description = 'E2E 테스트용 상품',
    price = 10000,
    category = '전자제품',
    stock = 500,
    version = 0,
    updated_at = NOW();

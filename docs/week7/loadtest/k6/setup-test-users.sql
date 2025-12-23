-- K6 Coupon Concurrency Test - User Setup Script
-- 테스트에 필요한 사용자 데이터를 미리 생성합니다.
--
-- Usage:
--   mysql -h localhost -u root -p ecommerce < setup-test-users.sql
--
-- 생성되는 사용자 범위:
--   - extremeConcurrency: userId 1000 ~ 100999 (100 VUs * 1000 offset)
--   - sequentialIssue: userId 200000 ~ 200049 (50 iterations)
--   - rampUpTest: userId 300000 ~ 304999 (50 VUs * 100 offset)

-- 1. extremeConcurrency 시나리오용 사용자 (1000-100999)
-- 100 VUs, 각 VU가 최대 100개 iteration 가능하므로 101,000개 필요
-- 하지만 실제로는 100 iterations만 실행되므로 약 10,000개면 충분
INSERT INTO users (id, name, email, balance, created_at, updated_at)
SELECT
    1000 + (a.N + b.N * 1000) as id,
    CONCAT('K6TestUser-', 1000 + (a.N + b.N * 1000)) as name,
    CONCAT('k6test', 1000 + (a.N + b.N * 1000), '@example.com') as email,
    10000.00 as balance,
    NOW() as created_at,
    NOW() as updated_at
FROM
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
LIMIT 10000
ON DUPLICATE KEY UPDATE name=name; -- 이미 존재하면 무시

-- 2. sequentialIssue 시나리오용 사용자 (200000-200049)
INSERT INTO users (id, name, email, balance, created_at, updated_at)
SELECT
    200000 + N as id,
    CONCAT('K6TestUserSeq-', 200000 + N) as name,
    CONCAT('k6testseq', 200000 + N, '@example.com') as email,
    10000.00 as balance,
    NOW() as created_at,
    NOW() as updated_at
FROM
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) b
LIMIT 50
ON DUPLICATE KEY UPDATE name=name;

-- 3. rampUpTest 시나리오용 사용자 (300000-304999)
-- 최대 50 VUs * 100 offset = 5000개 범위
INSERT INTO users (id, name, email, balance, created_at, updated_at)
SELECT
    300000 + (a.N + b.N * 10 + c.N * 100) as id,
    CONCAT('K6TestUserRamp-', 300000 + (a.N + b.N * 10 + c.N * 100)) as name,
    CONCAT('k6testramp', 300000 + (a.N + b.N * 10 + c.N * 100), '@example.com') as email,
    10000.00 as balance,
    NOW() as created_at,
    NOW() as updated_at
FROM
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b,
    (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
     UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9
     UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
     UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19
     UNION SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24
     UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29
     UNION SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34
     UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38 UNION SELECT 39
     UNION SELECT 40 UNION SELECT 41 UNION SELECT 42 UNION SELECT 43 UNION SELECT 44
     UNION SELECT 45 UNION SELECT 46 UNION SELECT 47 UNION SELECT 48 UNION SELECT 49) c
LIMIT 5000
ON DUPLICATE KEY UPDATE name=name;

-- 결과 확인
SELECT
    '극한 동시성 사용자' as category,
    COUNT(*) as count,
    MIN(id) as min_id,
    MAX(id) as max_id
FROM users WHERE id BETWEEN 1000 AND 10999

UNION ALL

SELECT
    '순차 발급 사용자' as category,
    COUNT(*) as count,
    MIN(id) as min_id,
    MAX(id) as max_id
FROM users WHERE id BETWEEN 200000 AND 200049

UNION ALL

SELECT
    '램프업 사용자' as category,
    COUNT(*) as count,
    MIN(id) as min_id,
    MAX(id) as max_id
FROM users WHERE id BETWEEN 300000 AND 304999;

-- 완료 메시지
SELECT '✅ Test users created successfully!' as message;
SELECT CONCAT('Total users created: ', COUNT(*)) as summary
FROM users WHERE id >= 1000 AND (id < 11000 OR (id >= 200000 AND id < 200050) OR (id >= 300000 AND id < 305000));

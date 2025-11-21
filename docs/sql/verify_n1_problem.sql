-- ========================================
-- N+1 문제 검증 스크립트
-- MySQL Workbench에서 실행
-- ========================================

-- Step 1: General Log 활성화 및 초기화
-- ========================================
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';
TRUNCATE TABLE mysql.general_log;

SELECT '✅ General Log 활성화 완료! 이제 API를 호출하세요:' AS message,
       'curl "http://localhost:8080/api/orders?userId=1"' AS command;

-- ========================================
-- Step 2: API 호출 후 10초 뒤에 아래 쿼리 실행
-- ========================================

-- 2-1. 총 SELECT 쿼리 개수 확인
SELECT
    'Step 1: 총 SELECT 쿼리 개수' AS check_point,
    COUNT(*) AS total_queries,
    CASE
        WHEN COUNT(*) <= 5 THEN '✅ PASS (Batch Fetch 동작)'
        WHEN COUNT(*) BETWEEN 6 AND 10 THEN '⚠️ 주의 (확인 필요)'
        ELSE '❌ FAIL (N+1 문제 존재)'
    END AS result
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND);

-- 2-2. 테이블별 쿼리 개수 및 Batch 여부
SELECT
    'Step 2: 테이블별 쿼리 분석' AS check_point,
    CASE
        WHEN argument LIKE '%FROM orders%' OR argument LIKE '%FROM `orders`%' THEN 'orders'
        WHEN argument LIKE '%FROM order_items%' OR argument LIKE '%FROM `order_items`%' THEN 'order_items'
        WHEN argument LIKE '%FROM products%' OR argument LIKE '%FROM `products`%' THEN 'products'
        ELSE 'other'
    END AS table_name,
    COUNT(*) AS query_count,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 1 ELSE 0 END) AS batch_count,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 0 ELSE 1 END) AS individual_count,
    CASE
        WHEN COUNT(*) = 1 AND argument LIKE '%IN (%' THEN '✅ Batch Fetch'
        WHEN COUNT(*) = 1 THEN '✅ Single Query'
        WHEN COUNT(*) > 5 THEN '❌ N+1 Problem'
        ELSE '⚠️ Check Needed'
    END AS status
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)
GROUP BY table_name
ORDER BY query_count DESC;

-- 2-3. 실제 실행된 쿼리 목록 (최대 20개)
SELECT
    'Step 3: 실행된 쿼리 상세' AS check_point,
    event_time,
    SUBSTRING(argument, 1, 100) AS query_preview,
    CASE
        WHEN argument LIKE '%IN (%' THEN '✅ Batch (IN clause)'
        WHEN argument LIKE '%= ?%' THEN '⚠️ Individual'
        ELSE 'Other'
    END AS fetch_type,
    LENGTH(argument) AS query_length
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)
ORDER BY event_time
LIMIT 20;

-- 2-4. IN 절 사용 여부 확인 (Batch Fetch 증거)
SELECT
    'Step 4: IN 절 사용 확인' AS check_point,
    COUNT(*) AS queries_with_in_clause,
    CASE
        WHEN COUNT(*) >= 2 THEN '✅ Batch Fetch 확인됨'
        ELSE '❌ Batch Fetch 미사용'
    END AS result
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND argument LIKE '%IN (%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND);

-- 2-5. 최종 판정
SELECT
    '=== 최종 판정 ===' AS section,
    (SELECT COUNT(*) FROM mysql.general_log
     WHERE command_type = 'Query'
       AND argument LIKE 'select%'
       AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) AS total_queries,
    CASE
        WHEN (SELECT COUNT(*) FROM mysql.general_log
              WHERE command_type = 'Query'
                AND argument LIKE 'select%'
                AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) <= 5
        THEN '✅ PASS - N+1 문제 해결됨! Batch Fetch 동작 중'
        WHEN (SELECT COUNT(*) FROM mysql.general_log
              WHERE command_type = 'Query'
                AND argument LIKE 'select%'
                AND event_time >= DATE_SUB(NOW(), INTERVAL 30 SECOND)) BETWEEN 6 AND 10
        THEN '⚠️ 추가 확인 필요 - 쿼리가 예상보다 많음'
        ELSE '❌ FAIL - N+1 문제 존재! 개별 쿼리 반복 발생'
    END AS final_result;

-- ========================================
-- 추가: 인덱스 사용 확인 (EXPLAIN)
-- ========================================

-- order_items 쿼리 플랜 확인
EXPLAIN
SELECT oi.*
FROM order_items oi
WHERE oi.order_id IN (1, 2, 3, 4, 5);
-- ✅ 기대값: type=range, key=idx_order_id

-- products 쿼리 플랜 확인
EXPLAIN
SELECT p.*
FROM products p
WHERE p.id IN (1, 2, 3, 4, 5);
-- ✅ 기대값: type=range, key=PRIMARY

-- ========================================
-- 정리: General Log 비활성화 (선택사항)
-- ========================================
-- SET GLOBAL general_log = 'OFF';

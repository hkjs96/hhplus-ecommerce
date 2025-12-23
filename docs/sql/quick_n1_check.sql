-- ========================================
-- 빠른 N+1 검증 (MySQL Workbench)
-- ========================================
-- 사용법:
-- 1. 이 스크립트 전체를 MySQL Workbench에 붙여넣기
-- 2. 실행 (Ctrl+Shift+Enter 또는 ⚡ 아이콘)
-- 3. 다른 터미널에서: curl "http://localhost:8080/api/orders?userId=1"
-- 4. 10초 후 자동으로 결과 출력
-- ========================================

-- 준비
TRUNCATE TABLE mysql.general_log;
SET GLOBAL general_log = 'ON';
SET GLOBAL log_output = 'TABLE';

-- 안내 메시지
SELECT
    '⏳ 10초 안에 API를 호출하세요!' AS message,
    'curl "http://localhost:8080/api/orders?userId=1"' AS command;

-- 10초 대기
DO SLEEP(10);

-- 자동 분석
SELECT
    '📊 검증 결과' AS title,
    COUNT(*) AS 총_SELECT_쿼리,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 1 ELSE 0 END) AS Batch_쿼리,
    SUM(CASE WHEN argument LIKE '%IN (%' THEN 0 ELSE 1 END) AS 개별_쿼리,
    CASE
        WHEN COUNT(*) <= 5 THEN '✅ 성공 - Batch Fetch 동작'
        WHEN COUNT(*) <= 10 THEN '⚠️ 확인필요'
        ELSE '❌ 실패 - N+1 문제'
    END AS 판정
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND);

-- 테이블별 상세
SELECT
    '테이블별 분석' AS title,
    CASE
        WHEN argument LIKE '%order_items%' THEN 'order_items'
        WHEN argument LIKE '%orders%' THEN 'orders'
        WHEN argument LIKE '%products%' THEN 'products'
        ELSE 'other'
    END AS 테이블,
    COUNT(*) AS 쿼리개수,
    GROUP_CONCAT(
        CASE WHEN argument LIKE '%IN (%' THEN 'Batch' ELSE 'Individual' END
    ) AS 타입
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND)
GROUP BY 테이블;

-- 실제 쿼리 미리보기
SELECT
    '실행된 쿼리' AS title,
    SUBSTRING(argument, 1, 80) AS 쿼리,
    CASE WHEN argument LIKE '%IN (%' THEN '✅' ELSE '⚠️' END AS Batch여부
FROM mysql.general_log
WHERE command_type = 'Query'
  AND argument LIKE 'select%'
  AND event_time >= DATE_SUB(NOW(), INTERVAL 15 SECOND)
ORDER BY event_time;

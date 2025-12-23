package io.hhplus.ecommerce.performance;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabasePerformanceAnalysisTest {

    private static final Logger log = LoggerFactory.getLogger(DatabasePerformanceAnalysisTest.class);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("ecommerce_test")
        .withUsername("test")
        .withPassword("test")
        .withReuse(false);

    @DynamicPropertySource
	    static void configureProperties(DynamicPropertyRegistry registry) {
	        registry.add("spring.datasource.url", mysql::getJdbcUrl);
	        registry.add("spring.datasource.username", mysql::getUsername);
	        registry.add("spring.datasource.password", mysql::getPassword);
	        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
	    }

	    @Autowired
	    private PerformanceTestDataGenerator dataGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static boolean dataGenerated = false;

    @BeforeEach
    @Transactional
    void setUp() {
        if (!dataGenerated) {
            log.info("=".repeat(100));
            log.info("Generating test data - This will take a few minutes...");
            log.info("=".repeat(100));

            // 소규모 데이터셋 사용 (빠른 테스트)
            // 실제 성능 테스트 시에는 generateFullDataset() 사용
            dataGenerator.generateSmallDataset();
            dataGenerator.printStatistics();

            dataGenerated = true;
        }
    }

    // ============================================================ 
    // Test 1: 인기 상품 조회 EXPLAIN 분석
    // ============================================================ 

    @Test
    @Order(1)
    @DisplayName("EXPLAIN 분석 #1: 인기 상품 조회 (인덱스 없음)")
    void explainTopProducts_WithoutIndex() {
        log.info("\n" + "=".repeat(100));
        log.info("EXPLAIN Analysis #1: Top Products Query (WITHOUT Indexes)");
        log.info("=".repeat(100));

        // 인덱스가 없는 상태에서 실행 (초기 상태)
        String query = """
            SELECT
                oi.product_id,
                p.name,
                COUNT(*) AS sales_count,
                SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE o.status = 'COMPLETED'
              AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
            GROUP BY oi.product_id, p.name
            ORDER BY sales_count DESC
            LIMIT 5
            """;

        executeExplain(query, "Top Products (Without Index)");
    }

    @Test
    @Order(2)
    @DisplayName("성능 측정 #1: 인기 상품 조회 (인덱스 없음)")
    void measurePerformance_TopProducts_WithoutIndex() {
        log.info("\n" + "=".repeat(100));
        log.info("Performance Test #1: Top Products Query (WITHOUT Indexes)");
        log.info("=".repeat(100));

        String query = """
            SELECT
                oi.product_id,
                p.name,
                COUNT(*) AS sales_count,
                SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE o.status = 'COMPLETED'
              AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
            GROUP BY oi.product_id, p.name
            ORDER BY sales_count DESC
            LIMIT 5
            """;

        measureQueryPerformance(query, "Top Products (Without Index)", 10);
    }

    @Test
    @Order(3)
    @DisplayName("인덱스 생성: 성능 최적화 인덱스 추가")
    void createPerformanceIndexes() {
        log.info("\n" + "=".repeat(100));
        log.info("Creating Performance Optimization Indexes");
        log.info("=".repeat(100));

        // 1. 인기 상품 조회 최적화 인덱스
        createIndex("idx_status_paid_at", "orders", "status, paid_at");
        createIndex("idx_order_product_covering", "order_items", "order_id, product_id, quantity, subtotal");

        // 2. 주문 내역 조회 최적화 인덱스 (이미 존재하는지 확인)
        createIndexIfNotExists("idx_user_created", "orders", "user_id, created_at");
        createIndexIfNotExists("idx_order_id", "order_items", "order_id");
        createIndexIfNotExists("idx_product_id", "order_items", "product_id");

        // 3. 장바구니 조회 최적화 인덱스
        createIndex("idx_carts_user_id", "carts", "user_id");
        createIndex("idx_cart_items_cart_id", "cart_items", "cart_id");
        createIndex("idx_cart_items_product_id", "cart_items", "product_id");

        // 4. 쿠폰 조회 최적화 인덱스
        createIndex("idx_user_coupons_user_status", "user_coupons", "user_id, status");
        createIndex("idx_user_coupons_coupon_id", "user_coupons", "coupon_id");
        createIndex("idx_coupons_expires_at", "coupons", "expires_at");

        // 5. 상품 검색 최적화 인덱스 (이미 존재하는지 확인)
        createIndexIfNotExists("idx_category_created", "products", "category, created_at");

        log.info("✓ All performance indexes created successfully");
        log.info("=".repeat(100));
    }

    @Test
    @Order(4)
    @DisplayName("EXPLAIN 분석 #2: 인기 상품 조회 (인덱스 적용 후)")
    void explainTopProducts_WithIndex() {
        log.info("\n" + "=".repeat(100));
        log.info("EXPLAIN Analysis #2: Top Products Query (WITH Indexes)");
        log.info("=".repeat(100));

        String query = """
            SELECT
                oi.product_id,
                p.name,
                COUNT(*) AS sales_count,
                SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE o.status = 'COMPLETED'
              AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
            GROUP BY oi.product_id, p.name
            ORDER BY sales_count DESC
            LIMIT 5
            """;

        executeExplain(query, "Top Products (With Index)");
    }

    @Test
    @Order(5)
    @DisplayName("성능 측정 #2: 인기 상품 조회 (인덱스 적용 후)")
    void measurePerformance_TopProducts_WithIndex() {
        log.info("\n" + "=".repeat(100));
        log.info("Performance Test #2: Top Products Query (WITH Indexes)");
        log.info("=".repeat(100));

        String query = """
            SELECT
                oi.product_id,
                p.name,
                COUNT(*) AS sales_count,
                SUM(oi.subtotal) AS revenue
            FROM order_items oi
            JOIN orders o ON oi.order_id = o.id
            JOIN products p ON oi.product_id = p.id
            WHERE o.status = 'COMPLETED'
              AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
            GROUP BY oi.product_id, p.name
            ORDER BY sales_count DESC
            LIMIT 5
            """;

        measureQueryPerformance(query, "Top Products (With Index)", 10);
    }

    // ============================================================ 
    // Test 2: 주문 내역 조회 EXPLAIN 분석
    // ============================================================ 

    @Test
    @Order(6)
    @DisplayName("EXPLAIN 분석 #3: 주문 내역 조회 (단일 쿼리)")
    void explainOrdersWithItems() {
        log.info("\n" + "=".repeat(100));
        log.info("EXPLAIN Analysis #3: Orders with Items Query");
        log.info("=".repeat(100));

        String query = """
            SELECT
                o.id, o.order_number, o.total_amount, o.status, o.created_at,
                oi.id AS item_id, oi.product_id, p.name AS product_name,
                oi.quantity, oi.unit_price, oi.subtotal
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN products p ON oi.product_id = p.id
            WHERE o.user_id = 1
            ORDER BY o.created_at DESC
            """;

        executeExplain(query, "Orders with Items");
    }

    @Test
    @Order(7)
    @DisplayName("성능 측정 #3: 주문 내역 조회")
    void measurePerformance_OrdersWithItems() {
        log.info("\n" + "=".repeat(100));
        log.info("Performance Test #3: Orders with Items Query");
        log.info("=".repeat(100));

        String query = """
            SELECT
                o.id, o.order_number, o.total_amount, o.status, o.created_at,
                oi.id AS item_id, oi.product_id, p.name AS product_name,
                oi.quantity, oi.unit_price, oi.subtotal
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN products p ON oi.product_id = p.id
            WHERE o.user_id = 1
            ORDER BY o.created_at DESC
            """;

        measureQueryPerformance(query, "Orders with Items", 10);
    }

    // ============================================================ 
    // Test 3: 장바구니 조회 EXPLAIN 분석
    // ============================================================ 

    @Test
    @Order(8)
    @DisplayName("EXPLAIN 분석 #4: 장바구니 조회")
    void explainCartWithItems() {
        log.info("\n" + "=".repeat(100));
        log.info("EXPLAIN Analysis #4: Cart with Items Query");
        log.info("=".repeat(100));

        String query = """
            SELECT
                c.id, c.user_id, c.created_at, c.updated_at,
                ci.id AS item_id, ci.product_id, p.name AS product_name,
                p.price, ci.quantity, ci.created_at
            FROM carts c
            LEFT JOIN cart_items ci ON c.id = ci.cart_id
            LEFT JOIN products p ON ci.product_id = p.id
            WHERE c.user_id = 1
            ORDER BY ci.created_at DESC
            """;

        executeExplain(query, "Cart with Items");
    }

    // ============================================================ 
    // Test 4: 쿠폰 조회 EXPLAIN 분석
    // ============================================================ 

    @Test
    @Order(9)
    @DisplayName("EXPLAIN 분석 #5: 쿠폰 조회")
    void explainUserCoupons() {
        log.info("\n" + "=".repeat(100));
        log.info("EXPLAIN Analysis #5: User Coupons Query");
        log.info("=".repeat(100));

        String query = """
            SELECT
                uc.id, uc.user_id, uc.coupon_id, uc.status, uc.issued_at, uc.used_at,
                c.name AS coupon_name, c.discount_rate, c.end_date
            FROM user_coupons uc
            JOIN coupons c ON uc.coupon_id = c.id
            WHERE uc.user_id = 1
              AND uc.status = 'AVAILABLE'
            ORDER BY uc.issued_at DESC
            """;

        executeExplain(query, "User Coupons");
    }

    // ============================================================ 
    // Helper Methods
    // ============================================================ 

    private void executeExplain(String query, String queryName) {
        log.info("\n" + "-".repeat(100));
        log.info("Query: {}", queryName);
        log.info("-".repeat(100));

        String explainQuery = "EXPLAIN " + query;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(explainQuery);

        // 테이블 헤더
        log.info(String.format("%-4s %-12s %-10s %-6s %-25s %-25s %-10s %-20s %-10s %-10s %-30s",
            "id", "select_type", "table", "type", "possible_keys", "key", "key_len", "ref", "rows", "filtered", "Extra"));
        log.info("-".repeat(100));

        // 결과 출력
        for (Map<String, Object> row : results) {
            log.info(String.format("%-4s %-12s %-10s %-6s %-25s %-25s %-10s %-20s %-10s %-10s %-30s",
                row.get("id"),
                row.get("select_type"),
                row.get("table"),
                row.get("type"),
                truncate(String.valueOf(row.get("possible_keys")), 25),
                truncate(String.valueOf(row.get("key")), 25),
                row.get("key_len"),
                truncate(String.valueOf(row.get("ref")), 20),
                row.get("rows"),
                row.get("filtered"),
                truncate(String.valueOf(row.get("Extra")), 30)
            ));
        }

        log.info("-".repeat(100));

        // 성능 분석 요약
        analyzeExplainResult(results);
    }

    private void analyzeExplainResult(List<Map<String, Object>> results) {
        log.info("\n📊 Analysis Summary:");

        long totalRows = 0;
        boolean hasFullTableScan = false;
        boolean usesTemporary = false;
        boolean usesFilesort = false;
        boolean usingIndex = false;

        for (Map<String, Object> row : results) {
            String type = String.valueOf(row.get("type"));
            String extra = String.valueOf(row.get("Extra"));
            Object rowsObj = row.get("rows");
            long rows = rowsObj != null ? Long.parseLong(String.valueOf(rowsObj)) : 0;

            totalRows += rows;

            if ("ALL".equals(type)) {
                hasFullTableScan = true;
                log.warn("  ⚠️  Table '{}' uses FULL TABLE SCAN ({} rows)", row.get("table"), rows);
            }

            if (extra != null) {
                if (extra.contains("Using temporary")) {
                    usesTemporary = true;
                }
                if (extra.contains("Using filesort")) {
                    usesFilesort = true;
                }
                if (extra.contains("Using index")) {
                    usingIndex = true;
                }
            }

            String key = String.valueOf(row.get("key"));
            if (key != null && !"null".equals(key)) {
                log.info("  ✓ Table '{}' uses index: {}", row.get("table"), key);
            }
        }

        log.info("\n📈 Performance Indicators:");
        log.info("  Total Rows Examined: {}", totalRows);
        log.info("  Full Table Scan: {}", hasFullTableScan ? "❌ YES" : "✅ NO");
        log.info("  Using Temporary: {}", usesTemporary ? "⚠️  YES" : "✅ NO");
        log.info("  Using Filesort: {}", usesFilesort ? "⚠️  YES" : "✅ NO");
        log.info("  Using Index (Covering): {}", usingIndex ? "✅ YES" : "❌ NO");

        log.info("=".repeat(100));
    }

    private void measureQueryPerformance(String query, String queryName, int iterations) {
        log.info("\n" + "-".repeat(100));
        log.info("Query: {}", queryName);
        log.info("Iterations: {}", iterations);
        log.info("-".repeat(100));

        long[] times = new long[iterations];
        long sum = 0;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            jdbcTemplate.queryForList(query);
            long endTime = System.currentTimeMillis();

            times[i] = endTime - startTime;
            sum += times[i];

            log.debug("  Iteration {}: {} ms", i + 1, times[i]);
        }

        long avgTime = sum / iterations;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;

        for (long time : times) {
            if (time < minTime) minTime = time;
            if (time > maxTime) maxTime = time;
        }

        log.info("\n⏱️  Performance Results:");
        log.info("  Average: {} ms", avgTime);
        log.info("  Min: {} ms", minTime);
        log.info("  Max: {} ms", maxTime);
        log.info("=".repeat(100));
    }

    private void createIndex(String indexName, String tableName, String columns) {
        try {
            String sql = String.format("CREATE INDEX %s ON %s(%s)", indexName, tableName, columns);
            jdbcTemplate.execute(sql);
            log.info("✓ Created index: {} on {}({})", indexName, tableName, columns);
        } catch (Exception e) {
            log.warn("Index {} already exists or failed to create: {}", indexName, e.getMessage());
        }
    }

    private void createIndexIfNotExists(String indexName, String tableName, String columns) {
        try {
            // 인덱스 존재 여부 확인
            String checkSql = "SHOW INDEX FROM " + tableName + " WHERE Key_name = '" + indexName + "'";
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(checkSql);

            if (indexes.isEmpty()) {
                createIndex(indexName, tableName, columns);
            } else {
                log.debug("Index {} already exists on {}", indexName, tableName);
            }
        } catch (Exception e) {
            log.warn("Failed to check/create index {}: {}", indexName, e.getMessage());
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null || "null".equals(str)) {
            return "-";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}

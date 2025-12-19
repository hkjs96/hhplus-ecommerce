package io.hhplus.ecommerce.infrastructure.init;

import io.hhplus.ecommerce.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * K6 부하 테스트를 위한 테스트 데이터 자동 생성
 * <p>
 * 애플리케이션 시작 시 자동으로 테스트용 사용자를 생성합니다.
 * 이미 존재하는 사용자는 건너뜁니다.
 * <p>
 * 생성되는 사용자:
 * - default: 1명 (userId 1) - config.js 기본값
 * - extremeConcurrency: 10,000명 (userId 1000-10999)
 * - sequentialIssue: 100명 (userId 200000-200099)
 * - rampUpTest: 10,000명 (userId 300000-309999)
 * <p>
 * 총 20,101명
 */
@Slf4j
@Component
@Profile("local")  // test 프로필 제거 - 통합 테스트와 충돌 방지
@RequiredArgsConstructor
public class LoadTestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("=== K6 Load Test Data Initializer START ===");

        long startTime = System.currentTimeMillis();
        int totalCreated = 0;

        // 0. K6 기본 테스트 사용자 (userId: 1) - config.js의 기본값
        totalCreated += createUsersIfNotExist(1, 1, "K6Test-Default");

        // 1. extremeConcurrency 시나리오용 사용자 (1000-10999)
        totalCreated += createUsersIfNotExist(1000, 10999, "K6Test-Extreme");

        // 2. sequentialIssue 시나리오용 사용자 (200000-200099)
        totalCreated += createUsersIfNotExist(200000, 200099, "K6Test-Seq");

        // 3. rampUpTest 시나리오용 사용자 (300000-309999)
        // rampUpTest는 최대 50 VUs * 200 iterations = 10,000개 필요
        totalCreated += createUsersIfNotExist(300000, 309999, "K6Test-Ramp");

        long duration = System.currentTimeMillis() - startTime;

        log.info("=== K6 Load Test Data Initializer END ===");
        log.info("Created {} new test users in {}ms", totalCreated, duration);
        if (totalCreated == 0) {
            log.info("All test users already exist - skipped creation");
        }
    }

    /**
     * 지정된 범위의 사용자를 생성합니다 (이미 존재하면 skip)
     * <p>
     * JPA 대신 JDBC Template을 사용하여 직접 SQL INSERT를 실행합니다.
     * 이유: @GeneratedValue(IDENTITY) 전략에서 ID를 직접 설정하면 JPA가 detached 상태로 인식하기 때문
     *
     * @param startId   시작 ID
     * @param endId     종료 ID (포함)
     * @param namePrefix 이름 접두사
     * @return 생성된 사용자 수
     */
    private int createUsersIfNotExist(long startId, long endId, String namePrefix) {
        log.info("Creating test users: {} - {} ({})", startId, endId, namePrefix);

        int created = 0;
        String insertSql = "INSERT INTO users (id, email, username, balance, version, created_at, updated_at) " +
                           "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                           "ON DUPLICATE KEY UPDATE id = id";

        for (long id = startId; id <= endId; id++) {
            // 네이티브 SQL INSERT로 사용자 생성
            String email = String.format("k6test%d@loadtest.com", id);
            String username = String.format("%s-%d", namePrefix, id);
            // userId 1은 ranking 테스트의 기본 사용자로 충분한 잔액 제공
            // K6 테스트: 3.5분 동안 ~10,000회 주문 × 평균 1,350,000원 = 13,500,000,000원 필요
            long balance = (id == 1) ? 20_000_000_000L : 10_000L;  // 200억원 (여유 확보)
            long version = 0L;

            int affectedRows = jdbcTemplate.update(insertSql, id, email, username, balance, version);
            if (affectedRows == 1) {
                created++;
            }

            // 진행 상황 로깅 (매 1000명마다)
            if (created % 1000 == 0) {
                log.info("Progress: {} users created so far...", created);
            }
        }

        log.info("Created {} users for range {} - {}", created, startId, endId);
        return created;
    }
}

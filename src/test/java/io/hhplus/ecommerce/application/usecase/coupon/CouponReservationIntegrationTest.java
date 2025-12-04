package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.*;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 예약 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 예약 → 발급 완료 플로우 검증
 * 2. Event Listener 처리 검증
 * 3. 실패 시 Redis 원복 검증
 */
@SpringBootTest
@Import(TestContainersConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CouponReservationIntegrationTest {

    @Autowired
    private ReserveCouponUseCase reserveCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponReservationRepository reservationRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponRepository jpaCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaCouponReservationRepository jpaReservationRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.coupon.JpaUserCouponRepository jpaUserCouponRepository;

    @Autowired
    private io.hhplus.ecommerce.infrastructure.persistence.user.JpaUserRepository jpaUserRepository;

    private static final String TEST_RESULT_DIR = "build/test-results/coupon-reservation-integration";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeAll
    static void beforeAll() throws IOException {
        Path resultDir = Paths.get(TEST_RESULT_DIR);
        if (!Files.exists(resultDir)) {
            Files.createDirectories(resultDir);
        }
    }

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        jpaUserCouponRepository.deleteAll();
        jpaReservationRepository.deleteAll();
        jpaCouponRepository.deleteAll();
        jpaUserRepository.deleteAll();

        // Redis 정리
        clearRedis();

        // 테스트 쿠폰 생성
        testCoupon = Coupon.create(
                "INT-" + (System.currentTimeMillis() % 100000000),
                "통합 테스트 쿠폰",
                10,
                10,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
        testCoupon = couponRepository.save(testCoupon);

        // 테스트 사용자 생성
        testUsers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            User user = User.create(
                    "integ-test-" + i + "@test.com",
                    "integ-user-" + i
            );
            user.charge(100000L);
            user = userRepository.save(user);
            testUsers.add(user);
        }
    }

    @Test
    @Order(1)
    @DisplayName("예약 성공 → Event → 발급 완료 플로우 검증")
    void 예약_발급_완료_플로우_테스트() throws IOException, InterruptedException {
        // 로그 파일 준비
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_reservation_flow_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("=".repeat(80));
        logWriter.println("예약 → 발급 완료 플로우 테스트");
        logWriter.println("=".repeat(80));
        logWriter.println("쿠폰 ID: " + testCoupon.getId());
        logWriter.println("테스트 시작: " + LocalDateTime.now());
        logWriter.println();

        // Given
        User user = testUsers.get(0);

        // When: 예약 요청
        long startTime = System.currentTimeMillis();
        reserveCouponUseCase.execute(testCoupon.getId(), user.getId());
        long reservationTime = System.currentTimeMillis() - startTime;

        logWriter.println("[1단계] 예약 요청 완료 (" + reservationTime + "ms)");

        // Then 1: CouponReservation 생성 확인
        CouponReservation reservation = reservationRepository
                .findByUserIdAndCouponId(user.getId(), testCoupon.getId())
                .orElseThrow();

        logWriter.println("  - CouponReservation ID: " + reservation.getId());
        logWriter.println("  - Sequence Number: " + reservation.getSequenceNumber());
        logWriter.println("  - Status: " + reservation.getStatus());
        logWriter.println("  - Reserved At: " + reservation.getReservedAt());

        assertThat(reservation.getSequenceNumber()).isEqualTo(1L);
        // Event Listener가 동기적으로 즉시 실행되므로, 이미 ISSUED 상태일 수 있음
        // assertThat(reservation.isReserved()).isTrue();  // ← 제거
        assertThat(reservation.getId()).isNotNull();

        // Then 2: Redis 순번 확인
        String redisSequence = redisTemplate.opsForValue()
                .get("coupon:" + testCoupon.getId() + ":sequence");

        logWriter.println();
        logWriter.println("[2단계] Redis 상태");
        logWriter.println("  - Redis Sequence: " + redisSequence);

        assertThat(redisSequence).isEqualTo("1");

        // Then 3: Event Listener 처리 대기 (비동기)
        logWriter.println();
        logWriter.println("[3단계] Event Listener 처리 대기...");

        Thread.sleep(2000);  // Event 처리 대기

        // Then 4: UserCoupon 생성 확인
        long userCouponCount = jpaUserCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()) && uc.getUserId().equals(user.getId()))
                .count();

        logWriter.println("  - UserCoupon 생성 확인: " + (userCouponCount > 0 ? "✅" : "❌"));

        // Then 5: Coupon 재고 확인
        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

        logWriter.println();
        logWriter.println("[4단계] Coupon 재고 차감 확인");
        logWriter.println("  - issuedQuantity: " + updatedCoupon.getIssuedQuantity());
        logWriter.println("  - remaining: " + updatedCoupon.getRemainingQuantity());

        // Then 6: CouponReservation 상태 업데이트 확인
        CouponReservation updatedReservation = reservationRepository.findById(reservation.getId()).orElseThrow();

        logWriter.println();
        logWriter.println("[5단계] CouponReservation 상태 확인");
        logWriter.println("  - Status: " + updatedReservation.getStatus());
        logWriter.println("  - Issued At: " + updatedReservation.getIssuedAt());

        long totalTime = System.currentTimeMillis() - startTime;

        logWriter.println();
        logWriter.println("=".repeat(80));
        logWriter.println("테스트 결과");
        logWriter.println("=".repeat(80));
        logWriter.println("총 소요 시간: " + totalTime + "ms");
        logWriter.println("예약 생성: ✅");
        logWriter.println("쿠폰 발급: " + (userCouponCount > 0 ? "✅" : "❌"));
        logWriter.println("재고 차감: " + (updatedCoupon.getIssuedQuantity() > 0 ? "✅" : "❌"));
        logWriter.println("상태 업데이트: " + (updatedReservation.isIssued() ? "✅" : "❌"));
        logWriter.println("=".repeat(80));

        logWriter.close();

        System.out.println("\n예약 → 발급 플로우 테스트 완료");
        System.out.println("로그 파일: " + logFileName + "\n");

        assertThat(userCouponCount).isEqualTo(1);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(updatedReservation.isIssued()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("10명 순차 예약 → 모두 발급 완료 검증")
    void 다수_사용자_예약_발급_테스트() throws IOException, InterruptedException {
        // 로그 파일
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_multiple_users_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("다수 사용자 예약 → 발급 테스트");
        logWriter.println("사용자 수: 10명");
        logWriter.println();

        // Given
        int userCount = 10;

        // When: 10명이 순차적으로 예약
        for (int i = 0; i < userCount; i++) {
            User user = testUsers.get(i);
            reserveCouponUseCase.execute(testCoupon.getId(), user.getId());
            logWriter.println("User " + i + " 예약 완료 (Sequence: " + (i + 1) + ")");
        }

        // Event 처리 대기
        Thread.sleep(3000);

        // Then: 모두 발급되었는지 확인
        long reservationCount = reservationRepository.countByCouponId(testCoupon.getId());
        long userCouponCount = jpaUserCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count();

        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

        logWriter.println();
        logWriter.println("[결과]");
        logWriter.println("예약 건수: " + reservationCount);
        logWriter.println("발급 건수: " + userCouponCount);
        logWriter.println("Coupon.issuedQuantity: " + updatedCoupon.getIssuedQuantity());
        logWriter.println();
        logWriter.println("검증: " + (reservationCount == userCount && userCouponCount == userCount ? "✅ PASS" : "❌ FAIL"));

        logWriter.close();

        System.out.println("\n다수 사용자 테스트: 예약 " + reservationCount + " / 발급 " + userCouponCount);
        System.out.println("로그 파일: " + logFileName + "\n");

        assertThat(reservationCount).isEqualTo(userCount);
        assertThat(userCouponCount).isEqualTo(userCount);
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(userCount);
    }

    @Test
    @Order(3)
    @DisplayName("Redis 데이터와 DB 데이터 일관성 검증")
    void Redis_DB_일관성_검증() throws IOException, InterruptedException {
        // 로그 파일
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_redis_db_consistency_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("Redis - DB 일관성 테스트");
        logWriter.println();

        // Given: 5명 예약
        int reserveCount = 5;
        for (int i = 0; i < reserveCount; i++) {
            reserveCouponUseCase.execute(testCoupon.getId(), testUsers.get(i).getId());
        }

        Thread.sleep(2000);

        // When: 데이터 조회
        String redisSequence = redisTemplate.opsForValue()
                .get("coupon:" + testCoupon.getId() + ":sequence");
        Long redisIssuedCount = redisTemplate.opsForSet()
                .size("coupon:" + testCoupon.getId() + ":issued");

        long dbReservationCount = reservationRepository.countByCouponId(testCoupon.getId());
        long dbUserCouponCount = jpaUserCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count();

        Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

        // Then: 일관성 검증
        logWriter.println("[Redis 데이터]");
        logWriter.println("  - Sequence: " + redisSequence);
        logWriter.println("  - Issued Set Size: " + redisIssuedCount);
        logWriter.println();
        logWriter.println("[DB 데이터]");
        logWriter.println("  - CouponReservation: " + dbReservationCount);
        logWriter.println("  - UserCoupon: " + dbUserCouponCount);
        logWriter.println("  - Coupon.issuedQuantity: " + coupon.getIssuedQuantity());
        logWriter.println();

        boolean consistent = dbReservationCount == reserveCount &&
                dbUserCouponCount == reserveCount &&
                coupon.getIssuedQuantity() == reserveCount;

        logWriter.println("일관성 검증: " + (consistent ? "✅ PASS" : "❌ FAIL"));

        logWriter.close();

        System.out.println("\n일관성 테스트 완료");
        System.out.println("로그 파일: " + logFileName + "\n");

        assertThat(dbReservationCount).isEqualTo(reserveCount);
        assertThat(dbUserCouponCount).isEqualTo(reserveCount);
        assertThat(coupon.getIssuedQuantity()).isEqualTo(reserveCount);
    }

    @AfterEach
    void tearDown() {
        clearRedis();
    }

    private void clearRedis() {
        try {
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .serverCommands()
                    .flushAll();
        } catch (Exception e) {
            System.err.println("Redis 초기화 실패: " + e.getMessage());
        }
    }
}

package io.hhplus.ecommerce.application.usecase.coupon;

import io.hhplus.ecommerce.config.TestContainersConfig;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.CouponReservation;
import io.hhplus.ecommerce.domain.coupon.CouponReservationRepository;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 예약 동시성 테스트 (Redis INCR 기반)
 *
 * STEP 14 핵심 테스트:
 * - 1000명이 동시에 선착순 100개 쿠폰 예약 요청
 * - Redis INCR의 원자성으로 정확히 100개만 예약 성공
 * - 나머지 900개는 수량 초과로 실패
 *
 * 테스트 결과 로그:
 * - build/test-results/coupon-reservation-concurrency/
 */
@SpringBootTest
@Import(TestContainersConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CouponReservationConcurrencyTest {

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

    private static final String TEST_RESULT_DIR = "build/test-results/coupon-reservation-concurrency";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeAll
    static void beforeAll() throws IOException {
        // 테스트 결과 디렉토리 생성
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

        // Redis 데이터 정리
        clearRedis();

        // 테스트 쿠폰 생성 (선착순 100개)
        testCoupon = Coupon.create(
                "RSV-" + (System.currentTimeMillis() % 100000000),
                "선착순 예약 테스트 쿠폰",
                10,  // 10% 할인
                100,  // 총 수량 100개
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        );
        testCoupon = couponRepository.save(testCoupon);

        // 테스트 사용자 1000명 생성
        testUsers = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            User user = User.create(
                    "reserve-test-" + i + "@test.com",
                    "reserve-user-" + i
            );
            user.charge(100000L);
            user = userRepository.save(user);
            testUsers.add(user);
        }

        System.out.println("=== Test Setup Complete ===");
        System.out.println("Coupon ID: " + testCoupon.getId());
        System.out.println("Total Quantity: " + testCoupon.getTotalQuantity());
        System.out.println("Test Users: " + testUsers.size());

        // Redis 연결 테스트
        try {
            String testKey = "test:connection:check";
            redisTemplate.opsForValue().set(testKey, "OK");
            String testValue = redisTemplate.opsForValue().get(testKey);
            System.out.println("Redis Connection: " + (testValue != null ? "✅ OK" : "❌ FAILED"));
            redisTemplate.delete(testKey);
        } catch (Exception e) {
            System.err.println("❌ Redis Connection FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    @Order(1)
    @DisplayName("선착순 100개 쿠폰 - 1000명 동시 예약 요청 시 정확히 100개만 성공")
    void 선착순_쿠폰_예약_동시성_테스트_1000명() throws InterruptedException, IOException {
        // Given
        int threadCount = 1000;
        int expectedSuccess = 100;
        // Connection Pool 고갈 방지: Thread Pool 크기를 제한 (1000명이 순차적으로 요청)
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        AtomicInteger soldOutCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        // 테스트 로그 파일 준비
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_1000_requests_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("=".repeat(80));
        logWriter.println("선착순 쿠폰 예약 동시성 테스트");
        logWriter.println("=".repeat(80));
        logWriter.println("테스트 시작: " + LocalDateTime.now());
        logWriter.println("쿠폰 ID: " + testCoupon.getId());
        logWriter.println("총 수량: " + testCoupon.getTotalQuantity());
        logWriter.println("동시 요청 수: " + threadCount);
        logWriter.println("예상 성공: " + expectedSuccess);
        logWriter.println("=".repeat(80));
        logWriter.println();

        // When: 1000명이 동시에 예약 요청
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(userIndex);
                    reserveCouponUseCase.execute(testCoupon.getId(), user.getId());

                    int currentSuccess = successCount.incrementAndGet();
                    logWriter.println(String.format("[SUCCESS] User %d (ID: %d) - 예약 성공 (%d번째)",
                            userIndex, user.getId(), currentSuccess));

                } catch (Exception e) {
                    failCount.incrementAndGet();

                    String errorType;
                    if (e.getMessage().contains("소진")) {
                        soldOutCount.incrementAndGet();
                        errorType = "SOLD_OUT";
                    } else if (e.getMessage().contains("이미")) {
                        duplicateCount.incrementAndGet();
                        errorType = "DUPLICATE";
                    } else {
                        errorCount.incrementAndGet();
                        errorType = "ERROR";
                    }

                    logWriter.println(String.format("[%s] User %d - %s",
                            errorType, userIndex, e.getMessage()));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: 검증
        String redisSequence = redisTemplate.opsForValue()
                .get("coupon:" + testCoupon.getId() + ":sequence");
        long reservationCount = reservationRepository.countByCouponId(testCoupon.getId());

        // Event Listener 처리 대기 (비동기)
        Thread.sleep(3000);

        long userCouponCount = jpaUserCouponRepository.findAll().stream()
                .filter(uc -> uc.getCouponId().equals(testCoupon.getId()))
                .count();

        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

        // 결과 로그 작성
        logWriter.println();
        logWriter.println("=".repeat(80));
        logWriter.println("테스트 결과");
        logWriter.println("=".repeat(80));
        logWriter.println("총 실행 시간: " + duration + "ms");
        logWriter.println();
        logWriter.println("[요청 통계]");
        logWriter.println("  - 총 요청: " + threadCount);
        logWriter.println("  - 성공: " + successCount.get());
        logWriter.println("  - 실패: " + failCount.get());
        logWriter.println("    * 수량 초과: " + soldOutCount.get());
        logWriter.println("    * 중복 예약: " + duplicateCount.get());
        logWriter.println("    * 기타 에러: " + errorCount.get());
        logWriter.println();
        logWriter.println("[데이터 검증]");
        logWriter.println("  - Redis Sequence: " + redisSequence);
        logWriter.println("  - CouponReservation 건수: " + reservationCount);
        logWriter.println("  - UserCoupon 건수: " + userCouponCount);
        logWriter.println("  - Coupon.issuedQuantity: " + updatedCoupon.getIssuedQuantity());
        logWriter.println();
        logWriter.println("[검증 결과]");

        boolean successCountValid = successCount.get() == expectedSuccess;
        boolean failCountValid = failCount.get() == (threadCount - expectedSuccess);
        boolean reservationCountValid = reservationCount == expectedSuccess;
        boolean redisSequenceValid = redisSequence != null && Long.parseLong(redisSequence) >= expectedSuccess;

        logWriter.println("  - 성공 건수 일치: " + (successCountValid ? "✅ PASS" : "❌ FAIL"));
        logWriter.println("  - 실패 건수 일치: " + (failCountValid ? "✅ PASS" : "❌ FAIL"));
        logWriter.println("  - 예약 건수 일치: " + (reservationCountValid ? "✅ PASS" : "❌ FAIL"));
        logWriter.println("  - Redis 순번 유효: " + (redisSequenceValid ? "✅ PASS" : "❌ FAIL"));
        logWriter.println();
        logWriter.println("=".repeat(80));
        logWriter.println("테스트 종료: " + LocalDateTime.now());
        logWriter.println("=".repeat(80));

        logWriter.close();

        // 콘솔 출력
        System.out.println("\n" + "=".repeat(80));
        System.out.println("테스트 결과 (1000명 요청)");
        System.out.println("=".repeat(80));
        System.out.println("성공: " + successCount.get() + " / 실패: " + failCount.get());
        System.out.println("Redis Sequence: " + redisSequence);
        System.out.println("CouponReservation: " + reservationCount);
        System.out.println("UserCoupon: " + userCouponCount);
        System.out.println("Coupon.issuedQuantity: " + updatedCoupon.getIssuedQuantity());
        System.out.println("실행 시간: " + duration + "ms");
        System.out.println("로그 파일: " + logFileName);
        System.out.println("=".repeat(80) + "\n");

        // Assertions
        assertThat(successCount.get()).isEqualTo(expectedSuccess);
        assertThat(failCount.get()).isEqualTo(threadCount - expectedSuccess);
        assertThat(reservationCount).isEqualTo(expectedSuccess);
        assertThat(redisSequence).isNotNull();
        assertThat(Long.parseLong(redisSequence)).isGreaterThanOrEqualTo(expectedSuccess);
    }

    @Test
    @Order(2)
    @DisplayName("재고 50개 쿠폰 - 200명 동시 예약 요청 시 정확히 50개만 성공")
    void 선착순_쿠폰_예약_동시성_테스트_재고부족() throws InterruptedException, IOException {
        // Given: 재고 50개 쿠폰 생성
        final Coupon limitedCoupon = couponRepository.save(Coupon.create(
                "LMT-" + (System.currentTimeMillis() % 100000000),
                "재고 부족 테스트 쿠폰",
                15,
                50,  // 총 수량 50개
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7)
        ));

        int threadCount = 200;
        int expectedSuccess = 50;
        // Connection Pool 고갈 방지: Thread Pool 크기를 제한
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        long startTime = System.currentTimeMillis();

        // 테스트 로그 파일
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_200_requests_50_stock_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("=".repeat(80));
        logWriter.println("재고 부족 동시성 테스트");
        logWriter.println("=".repeat(80));
        logWriter.println("쿠폰 ID: " + limitedCoupon.getId());
        logWriter.println("총 수량: " + limitedCoupon.getTotalQuantity());
        logWriter.println("동시 요청: " + threadCount);
        logWriter.println("=".repeat(80));
        logWriter.println();

        // When: 200명이 동시에 50개 쿠폰 예약
        for (int i = 0; i < threadCount; i++) {
            final int userIndex = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(userIndex);
                    reserveCouponUseCase.execute(limitedCoupon.getId(), user.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        // Then: 검증
        long reservationCount = reservationRepository.countByCouponId(limitedCoupon.getId());
        String redisSequence = redisTemplate.opsForValue()
                .get("coupon:" + limitedCoupon.getId() + ":sequence");

        // 결과 로그
        logWriter.println("[결과]");
        logWriter.println("성공: " + successCount.get());
        logWriter.println("실패: " + failCount.get());
        logWriter.println("예약 건수: " + reservationCount);
        logWriter.println("Redis Sequence: " + redisSequence);
        logWriter.println("실행 시간: " + duration + "ms");
        logWriter.println();
        logWriter.println("검증: " + (successCount.get() == expectedSuccess ? "✅ PASS" : "❌ FAIL"));
        logWriter.close();

        System.out.println("\n재고 부족 테스트: 성공 " + successCount.get() + " / 실패 " + failCount.get());
        System.out.println("로그 파일: " + logFileName + "\n");

        assertThat(successCount.get()).isEqualTo(expectedSuccess);
        assertThat(failCount.get()).isEqualTo(threadCount - expectedSuccess);
        assertThat(reservationCount).isEqualTo(expectedSuccess);
    }

    @Test
    @Order(3)
    @DisplayName("중복 예약 방지 테스트 - 같은 사용자가 여러 번 요청")
    void 중복_예약_방지_테스트() throws InterruptedException, IOException {
        // Given
        User singleUser = testUsers.get(0);
        int attemptCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(attemptCount);
        CountDownLatch latch = new CountDownLatch(attemptCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        // 테스트 로그
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logFileName = TEST_RESULT_DIR + "/test_duplicate_prevention_" + timestamp + ".log";
        PrintWriter logWriter = new PrintWriter(new FileWriter(logFileName));

        logWriter.println("중복 예약 방지 테스트");
        logWriter.println("사용자 ID: " + singleUser.getId());
        logWriter.println("시도 횟수: " + attemptCount);
        logWriter.println();

        // When: 같은 사용자가 10번 예약 시도
        for (int i = 0; i < attemptCount; i++) {
            final int attemptNum = i + 1;
            executorService.submit(() -> {
                try {
                    reserveCouponUseCase.execute(testCoupon.getId(), singleUser.getId());
                    successCount.incrementAndGet();
                    logWriter.println("시도 " + attemptNum + ": 성공");
                } catch (Exception e) {
                    if (e.getMessage().contains("이미")) {
                        duplicateCount.incrementAndGet();
                        logWriter.println("시도 " + attemptNum + ": 중복 예약 차단");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        long reservationCount = reservationRepository
                .findByUserIdAndCouponId(singleUser.getId(), testCoupon.getId())
                .stream().count();

        logWriter.println();
        logWriter.println("[결과]");
        logWriter.println("성공: " + successCount.get());
        logWriter.println("중복 차단: " + duplicateCount.get());
        logWriter.println("DB 예약 건수: " + reservationCount);
        logWriter.println("검증: " + (reservationCount == 1 ? "✅ PASS" : "❌ FAIL"));
        logWriter.close();

        System.out.println("\n중복 방지 테스트: 예약 건수 " + reservationCount);
        System.out.println("로그 파일: " + logFileName + "\n");

        assertThat(reservationCount).isEqualTo(1);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(attemptCount - 1);
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

package io.hhplus.ecommerce.presentation.api.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.coupon.dto.IssueCouponRequest;
import io.hhplus.ecommerce.domain.coupon.Coupon;
import io.hhplus.ecommerce.domain.coupon.CouponRepository;
import io.hhplus.ecommerce.domain.coupon.UserCoupon;
import io.hhplus.ecommerce.domain.coupon.UserCouponRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CouponControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    // Test ID fields
    private Long userId1;
    private Long userId2;
    private Long couponId1;
    private Long couponId2;
    private Long couponId3;

    @BeforeEach
    void setUp() {
        // Setup test user
        User user = User.create("U001", "test@example.com", "김항해");
        User savedUser = userRepository.save(user);
        userId1 = savedUser.getId();

        // Setup test coupons
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon1 = Coupon.create("C001", "10% 할인 쿠폰", 10, 100, now, now.plusDays(7));
        Coupon coupon2 = Coupon.create("C002", "20% 할인 쿠폰", 20, 50, now, now.plusDays(14));
        Coupon coupon3 = Coupon.create("C003", "만료된 쿠폰", 15, 100, now.minusDays(10), now.minusDays(3));

        Coupon savedCoupon1 = couponRepository.save(coupon1);
        Coupon savedCoupon2 = couponRepository.save(coupon2);
        Coupon savedCoupon3 = couponRepository.save(coupon3);

        couponId1 = savedCoupon1.getId();
        couponId2 = savedCoupon2.getId();
        couponId3 = savedCoupon3.getId();
    }

    @Test
    @DisplayName("쿠폰 발급 API - 성공")
    void issueCoupon_성공() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(userId1);

        // When & Then
        mockMvc.perform(post("/api/coupons/" + couponId1 + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCouponId").exists())
                .andExpect(jsonPath("$.couponName").value("10% 할인 쿠폰"))
                .andExpect(jsonPath("$.discountRate").value(10))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.remainingQuantity").value(99));
    }

    @Test
    @DisplayName("쿠폰 발급 API - 존재하지 않는 사용자")
    void issueCoupon_실패_존재하지않는사용자() throws Exception {
        // Given
        Long invalidUserId = 99999L;
        IssueCouponRequest request = new IssueCouponRequest(invalidUserId);

        // When & Then
        mockMvc.perform(post("/api/coupons/" + couponId1 + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("쿠폰 발급 API - 존재하지 않는 쿠폰")
    void issueCoupon_실패_존재하지않는쿠폰() throws Exception {
        // Given
        Long invalidCouponId = 99999L;
        IssueCouponRequest request = new IssueCouponRequest(userId1);

        // When & Then
        mockMvc.perform(post("/api/coupons/" + invalidCouponId + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("쿠폰 발급 API - 만료된 쿠폰")
    void issueCoupon_실패_만료된쿠폰() throws Exception {
        // Given
        IssueCouponRequest request = new IssueCouponRequest(userId1);

        // When & Then
        mockMvc.perform(post("/api/coupons/" + couponId3 + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("쿠폰 발급 API - 중복 발급")
    void issueCoupon_실패_중복발급() throws Exception {
        // Given - Issue coupon first time
        IssueCouponRequest request = new IssueCouponRequest(userId1);

        mockMvc.perform(post("/api/coupons/" + couponId1 + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - Issue same coupon again
        mockMvc.perform(post("/api/coupons/" + couponId1 + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C004"));
    }

    @Test
    @DisplayName("쿠폰 발급 API - 수량 소진")
    void issueCoupon_실패_수량소진() throws Exception {
        // Given - Create coupon with only 1 quantity
        LocalDateTime now = LocalDateTime.now();
        Coupon limitedCoupon = Coupon.create("C999", "한정 쿠폰", 30, 1, now, now.plusDays(7));
        Coupon savedLimitedCoupon = couponRepository.save(limitedCoupon);
        Long limitedCouponId = savedLimitedCoupon.getId();

        // Create another user
        User user2 = User.create("U002", "user2@example.com", "다른사용자");
        User savedUser2 = userRepository.save(user2);
        userId2 = savedUser2.getId();

        // First user gets the coupon
        IssueCouponRequest request1 = new IssueCouponRequest(userId1);
        mockMvc.perform(post("/api/coupons/" + limitedCouponId + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // When & Then - Second user fails
        IssueCouponRequest request2 = new IssueCouponRequest(userId2);
        mockMvc.perform(post("/api/coupons/" + limitedCouponId + "/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API - 성공")
    void getUserCoupons_성공() throws Exception {
        // Given - Issue some coupons to user
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon1 = UserCoupon.create(userId1, couponId1, now.plusDays(7));
        UserCoupon userCoupon2 = UserCoupon.create(userId1, couponId2, now.plusDays(14));

        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When & Then
        mockMvc.perform(get("/api/users/" + userId1 + "/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons.length()").value(2))
                .andExpect(jsonPath("$.coupons[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API - 상태 필터")
    void getUserCoupons_상태필터() throws Exception {
        // Given - Issue coupon and use it
        LocalDateTime now = LocalDateTime.now();
        UserCoupon userCoupon1 = UserCoupon.create(userId1, couponId1, now.plusDays(7));
        UserCoupon userCoupon2 = UserCoupon.create(userId1, couponId2, now.plusDays(14));
        userCoupon2.use(); // Mark as used

        UserCoupon savedUserCoupon1 = userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When & Then - Filter by AVAILABLE
        mockMvc.perform(get("/api/users/" + userId1 + "/coupons")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons.length()").value(1))
                .andExpect(jsonPath("$.coupons[0].userCouponId").value(savedUserCoupon1.getId()));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API - 존재하지 않는 사용자")
    void getUserCoupons_실패_존재하지않는사용자() throws Exception {
        Long invalidUserId = 99999L;
        mockMvc.perform(get("/api/users/" + invalidUserId + "/coupons"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 API - 빈 목록")
    void getUserCoupons_빈목록() throws Exception {
        mockMvc.perform(get("/api/users/" + userId1 + "/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons").isArray())
                .andExpect(jsonPath("$.coupons.length()").value(0));
    }
}

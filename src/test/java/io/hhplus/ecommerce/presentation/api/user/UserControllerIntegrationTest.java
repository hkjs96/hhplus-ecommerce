package io.hhplus.ecommerce.presentation.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hhplus.ecommerce.application.user.dto.ChargeBalanceRequest;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        User user = User.create("test@example.com", "김항해");
        user.charge(100000L);
        User savedUser = userRepository.save(user);
        testUserId = savedUser.getId();
    }

    @Test
    @DisplayName("사용자 조회 API - 성공")
    void getUser_성공() throws Exception {
        mockMvc.perform(get("/api/users/" + testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.username").value("김항해"))
                .andExpect(jsonPath("$.balance").value(100000L));
    }

    @Test
    @DisplayName("사용자 조회 API - 존재하지 않는 사용자")
    void getUser_실패_존재하지않는사용자() throws Exception {
        mockMvc.perform(get("/api/users/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("잔액 충전 API - 성공")
    void chargeBalance_성공() throws Exception {
        // Given
        ChargeBalanceRequest request = new ChargeBalanceRequest(50000L);

        // When & Then
        mockMvc.perform(post("/api/users/" + testUserId + "/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.balance").value(150000L))
                .andExpect(jsonPath("$.chargedAmount").value(50000L));

        // Verify balance updated in repository
        User user = userRepository.findById(testUserId).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(150000L);
    }

    @Test
    @DisplayName("잔액 충전 API - 음수 금액")
    void chargeBalance_실패_음수금액() throws Exception {
        // Given
        ChargeBalanceRequest request = new ChargeBalanceRequest(-10000L);

        // When & Then
        mockMvc.perform(post("/api/users/" + testUserId + "/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON002"));
    }

    @Test
    @DisplayName("잔액 충전 API - 0원 충전")
    void chargeBalance_실패_0원충전() throws Exception {
        // Given
        ChargeBalanceRequest request = new ChargeBalanceRequest(0L);

        // When & Then
        mockMvc.perform(post("/api/users/" + testUserId + "/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON002"));
    }

    @Test
    @DisplayName("잔액 충전 API - 존재하지 않는 사용자")
    void chargeBalance_실패_존재하지않는사용자() throws Exception {
        // Given
        ChargeBalanceRequest request = new ChargeBalanceRequest(50000L);

        // When & Then
        mockMvc.perform(post("/api/users/99999/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("포인트 조회 API - 성공")
    void getBalance_성공() throws Exception {
        mockMvc.perform(get("/api/users/" + testUserId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.balance").value(100000L));
    }

    @Test
    @DisplayName("포인트 조회 API - 존재하지 않는 사용자")
    void getBalance_실패_존재하지않는사용자() throws Exception {
        mockMvc.perform(get("/api/users/99999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("포인트 조회 API - 충전 후 잔액 확인")
    void getBalance_충전후_잔액확인() throws Exception {
        // Given - 50000원 충전
        ChargeBalanceRequest request = new ChargeBalanceRequest(50000L);
        mockMvc.perform(post("/api/users/" + testUserId + "/balance/charge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - 잔액 조회
        mockMvc.perform(get("/api/users/" + testUserId + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUserId))
                .andExpect(jsonPath("$.balance").value(150000L));  // 100000 + 50000

        // Verify balance in repository
        User user = userRepository.findById(testUserId).orElseThrow();
        assertThat(user.getBalance()).isEqualTo(150000L);
    }
}

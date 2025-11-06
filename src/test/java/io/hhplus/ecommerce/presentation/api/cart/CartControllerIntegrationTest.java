package io.hhplus.ecommerce.presentation.api.cart;

import io.hhplus.ecommerce.application.cart.dto.AddCartItemRequest;
import io.hhplus.ecommerce.application.cart.dto.DeleteCartItemRequest;
import io.hhplus.ecommerce.application.cart.dto.UpdateCartItemRequest;
import io.hhplus.ecommerce.domain.cart.Cart;
import io.hhplus.ecommerce.domain.cart.CartItem;
import io.hhplus.ecommerce.domain.cart.CartItemRepository;
import io.hhplus.ecommerce.domain.cart.CartRepository;
import io.hhplus.ecommerce.domain.product.Product;
import io.hhplus.ecommerce.domain.product.ProductRepository;
import io.hhplus.ecommerce.domain.user.User;
import io.hhplus.ecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CartControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 데이터 생성
        testUser = User.create("TEST_USER_001", "test@example.com", "테스트유저");
        testUser.charge(500000L);
        userRepository.save(testUser);

        // 테스트용 상품 데이터 생성
        testProduct1 = Product.create("TEST_P001", "테스트노트북", "고성능 노트북", 890000L, "전자제품", 10);
        testProduct2 = Product.create("TEST_P002", "테스트키보드", "기계식 키보드", 120000L, "주변기기", 20);
        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
    }

    // ====================================
    // 장바구니에 상품 추가 (POST /api/cart/items)
    // ====================================

    @Test
    @DisplayName("통합 테스트: 장바구니에 상품 추가 - 성공")
    void addItem_성공() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 2
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value("TEST_USER_001"))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].productId").value("TEST_P001"))
            .andExpect(jsonPath("$.items[0].name").value("테스트노트북"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(890000))
            .andExpect(jsonPath("$.items[0].subtotal").value(1780000))
            .andExpect(jsonPath("$.items[0].stockAvailable").value(true))
            .andExpect(jsonPath("$.totalAmount").value(1780000));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니에 중복 상품 추가 - 수량 증가")
    void addItem_성공_중복상품_수량증가() throws Exception {
        // Given: 장바구니에 이미 상품 존재
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);
        CartItem existingItem = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 3);
        cartItemRepository.save(existingItem);

        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 2
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items[0].quantity").value(5)); // 3 + 2
    }

    @Test
    @DisplayName("통합 테스트: 장바구니에 상품 추가 - 실패 (사용자 없음)")
    void addItem_실패_사용자없음() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "INVALID_USER",
              "productId": "TEST_P001",
              "quantity": 2
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound())
            
            .andExpect(jsonPath("$.code").value("U001"))
            .andExpect(jsonPath("$.message").value(containsString("사용자를 찾을 수 없습니다")));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니에 상품 추가 - 실패 (상품 없음)")
    void addItem_실패_상품없음() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "INVALID_PRODUCT",
              "quantity": 2
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound())
            
            .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니에 상품 추가 - 실패 (재고 부족)")
    void addItem_실패_재고부족() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 20
            }
            """;

        // When & Then (재고 10개)
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isConflict())
            
            .andExpect(jsonPath("$.code").value("P002"))
            .andExpect(jsonPath("$.message").value(containsString("재고가 부족합니다")));
    }

    // ====================================
    // 장바구니 조회 (GET /api/cart)
    // ====================================

    @Test
    @DisplayName("통합 테스트: 장바구니 조회 - 성공 (항목 있음)")
    void getCart_성공_항목있음() throws Exception {
        // Given
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);

        CartItem item1 = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 2);
        CartItem item2 = CartItem.create("ITEM-002", cart.getId(), "TEST_P002", 3);
        cartItemRepository.save(item1);
        cartItemRepository.save(item2);

        // When & Then
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andDo(print())
            .andExpect(status().isOk())
            
            .andExpect(jsonPath("$.userId").value("TEST_USER_001"))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].productId").value(anyOf(equalTo("TEST_P001"), equalTo("TEST_P002"))))
            .andExpect(jsonPath("$.totalAmount").value(2140000)); // 890000*2 + 120000*3
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 조회 - 성공 (빈 장바구니)")
    void getCart_성공_빈장바구니() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andDo(print())
            .andExpect(status().isOk())
            
            .andExpect(jsonPath("$.userId").value("TEST_USER_001"))
            .andExpect(jsonPath("$.items", hasSize(0)))
            .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 조회 - 재고 부족 상품 표시")
    void getCart_성공_재고부족_표시() throws Exception {
        // Given: 재고가 적은 상품 생성
        Product lowStockProduct = Product.create("LOW_STOCK_P001", "재고부족상품", "설명", 100000L, "테스트", 2);
        productRepository.save(lowStockProduct);

        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);

        // 장바구니 수량(5개)이 재고(2개)보다 많음
        CartItem item = CartItem.create("ITEM-001", cart.getId(), "LOW_STOCK_P001", 5);
        cartItemRepository.save(item);

        // When & Then
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andDo(print())
            .andExpect(status().isOk())
            
            .andExpect(jsonPath("$.items[0].stockAvailable").value(false)); // 재고 부족
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 조회 - 실패 (사용자 없음)")
    void getCart_실패_사용자없음() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/cart")
                .param("userId", "INVALID_USER"))
            .andDo(print())
            .andExpect(status().isNotFound())
            
            .andExpect(jsonPath("$.code").value("U001"));
    }

    // ====================================
    // 장바구니 상품 수량 변경 (PUT /api/cart/items)
    // ====================================

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 수량 변경 - 성공")
    void updateItem_성공() throws Exception {
        // Given
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);
        CartItem item = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 2);
        cartItemRepository.save(item);

        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 5
            }
            """;

        // When & Then
        mockMvc.perform(put("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            
            .andExpect(jsonPath("$.productId").value("TEST_P001"))
            .andExpect(jsonPath("$.quantity").value(5))
            .andExpect(jsonPath("$.subtotal").value(4450000)); // 890000 * 5
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 수량 변경 - 수량 0 시 삭제")
    void updateItem_성공_수량0_삭제() throws Exception {
        // Given
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);
        CartItem item = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 2);
        cartItemRepository.save(item);

        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 0
            }
            """;

        // When & Then
        mockMvc.perform(put("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            
            .andExpect(jsonPath("$.quantity").value(0))
            .andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 수량 변경 - 실패 (장바구니 없음)")
    void updateItem_실패_장바구니없음() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 5
            }
            """;

        // When & Then
        mockMvc.perform(put("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound())
            
            .andExpect(jsonPath("$.code").value("CART001"));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 수량 변경 - 실패 (재고 부족)")
    void updateItem_실패_재고부족() throws Exception {
        // Given
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);
        CartItem item = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 2);
        cartItemRepository.save(item);

        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 20
            }
            """;

        // When & Then (재고 10개)
        mockMvc.perform(put("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isConflict())
            
            .andExpect(jsonPath("$.code").value("P002"));
    }

    // ====================================
    // 장바구니 상품 삭제 (DELETE /api/cart/items)
    // ====================================

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 삭제 - 성공")
    void deleteItem_성공() throws Exception {
        // Given
        Cart cart = Cart.create("CART-TEST_USER_001", "TEST_USER_001");
        cartRepository.save(cart);
        CartItem item = CartItem.create("ITEM-001", cart.getId(), "TEST_P001", 2);
        cartItemRepository.save(item);

        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001"
            }
            """;

        // When & Then
        mockMvc.perform(delete("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk());

        // Verify: 항목이 실제로 삭제되었는지 확인
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 삭제 - 실패 (장바구니 없음)")
    void deleteItem_실패_장바구니없음() throws Exception {
        // Given
        String requestBody = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001"
            }
            """;

        // When & Then
        mockMvc.perform(delete("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound())
            
            .andExpect(jsonPath("$.code").value("CART001"));
    }

    @Test
    @DisplayName("통합 테스트: 장바구니 상품 삭제 - 실패 (장바구니 항목 없음)")
    void deleteItem_실패_항목없음() throws Exception {
        // Given: First add an item to create the cart, then try to delete a different product
        String addRequest = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 1
            }
            """;

        // Create a cart by adding an item
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addRequest))
            .andExpect(status().isCreated());

        // Now try to delete TEST_P002 which exists as a product but not in the cart
        String deleteRequest = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P002"
            }
            """;

        // When & Then (CART_ITEM_NOT_FOUND maps to NOT_FOUND per GlobalExceptionHandler)
        // Verify the cart exists first
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)));

        // Now attempt to delete non-existent item
        mockMvc.perform(delete("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteRequest))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CART002"));
    }

    // ====================================
    // 전체 시나리오 테스트
    // ====================================

    @Test
    @DisplayName("통합 테스트: 전체 시나리오 - 장바구니 추가/조회/수정/삭제")
    void fullScenario_장바구니_전체_흐름() throws Exception {
        // 1. 장바구니에 상품1 추가
        String addRequest1 = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 2
            }
            """;
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addRequest1))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items", hasSize(1)));

        // 2. 장바구니에 상품2 추가
        String addRequest2 = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P002",
              "quantity": 3
            }
            """;
        mockMvc.perform(post("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(addRequest2))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.items", hasSize(2)));

        // 3. 장바구니 조회
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.totalAmount").value(2140000)); // 890000*2 + 120000*3

        // 4. 상품1 수량 변경
        String updateRequest = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P001",
              "quantity": 5
            }
            """;
        mockMvc.perform(put("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(5));

        // 5. 장바구니 재조회 (수량 변경 확인)
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(4810000)); // 890000*5 + 120000*3

        // 6. 상품2 삭제
        String deleteRequest = """
            {
              "userId": "TEST_USER_001",
              "productId": "TEST_P002"
            }
            """;
        mockMvc.perform(delete("/api/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteRequest))
            .andExpect(status().isOk());

        // 7. 최종 장바구니 조회
        mockMvc.perform(get("/api/cart")
                .param("userId", "TEST_USER_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].productId").value("TEST_P001"))
            .andExpect(jsonPath("$.totalAmount").value(4450000)); // 890000*5
    }
}

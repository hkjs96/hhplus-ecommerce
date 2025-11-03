package io.hhplus.ecommerce.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(servers());
    }

    private Info apiInfo() {
        return new Info()
            .title("항해플러스 이커머스 API")
            .description("""
                ## 이커머스 시스템 API 명세서

                Week 2 과제: API 설계 및 시스템 아키텍처

                ### 핵심 기능
                - 상품 관리 (조회, 인기 상품)
                - 장바구니 (추가, 조회, 수정, 삭제)
                - 주문/결제 (주문 생성, 포인트 결제)
                - 쿠폰 시스템 (선착순 발급, 사용)
                - 사용자 (포인트 조회/충전)

                ### 가용성 패턴
                - Timeout (3초)
                - Retry (Exponential Backoff)
                - Fallback (빈 배열, Outbox)
                - Async (외부 API 전송)

                ### 기술 스택
                - Java 17
                - Spring Boot 3.5.7
                - Mock 데이터 (In-Memory)
                """)
            .version("1.0.0")
            .contact(new Contact()
                .name("항해플러스 백엔드")
                .url("https://github.com/hkjs96/hhplus-ecommerce"));
    }

    private List<Server> servers() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("Local Development Server");

        return List.of(localServer);
    }
}

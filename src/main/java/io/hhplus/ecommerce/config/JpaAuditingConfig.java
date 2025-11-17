package io.hhplus.ecommerce.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정
 *
 * @EnableJpaAuditing: JPA Auditing 기능 활성화
 * - @CreatedDate, @LastModifiedDate 자동 처리
 * - EntityListeners가 이벤트를 감지하여 자동으로 시간 필드 업데이트
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

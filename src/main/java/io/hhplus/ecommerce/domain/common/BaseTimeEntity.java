package io.hhplus.ecommerce.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BaseTimeEntity
 *
 * JPA Auditing을 사용하여 엔티티의 생성/수정 시간을 자동으로 관리
 *
 * 사용 이유:
 * 1. 중복 코드 제거: @PrePersist, @PreUpdate 훅 제거
 * 2. 일관성: 모든 엔티티에서 동일한 방식으로 시간 관리
 * 3. 유지보수성: Spring Data JPA가 자동으로 관리하므로 실수 방지
 *
 * 사용법:
 * - 엔티티에서 extends BaseTimeEntity
 * - @EnableJpaAuditing 설정 필요
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

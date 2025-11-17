package io.hhplus.ecommerce.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * BaseEntity (생성 시간만 관리)
 *
 * JPA Auditing을 사용하여 엔티티의 생성 시간을 자동으로 관리
 * 수정 시간이 필요 없는 Entity (Order, CartItem 등)에 사용
 *
 * 수정 시간이 필요한 경우 BaseTimeEntity 사용
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

package io.hhplus.ecommerce.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 테스트용 DataSource 설정
 *
 * 동시성 테스트를 위한 대용량 커넥션 풀 설정
 */
@TestConfiguration
public class TestDataSourceConfig {

    @Bean
    @Primary
    public DataSource testDataSource(TestContainersConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        // MySQL Container 설정 사용
        hikariConfig.setJdbcUrl(config.mysqlContainer().getJdbcUrl());
        hikariConfig.setUsername(config.mysqlContainer().getUsername());
        hikariConfig.setPassword(config.mysqlContainer().getPassword());
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 대용량 커넥션 풀 설정 (동시성 테스트용)
        hikariConfig.setMaximumPoolSize(150);
        hikariConfig.setMinimumIdle(50);
        hikariConfig.setConnectionTimeout(30000);  // 30초
        hikariConfig.setIdleTimeout(600000);       // 10분
        hikariConfig.setMaxLifetime(1800000);      // 30분
        hikariConfig.setPoolName("TestHikariPool");

        return new HikariDataSource(hikariConfig);
    }
}

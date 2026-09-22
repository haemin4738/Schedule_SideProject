package com.lifelog.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * infrastructure 모듈은 실행 가능한 앱이 아니므로(@SpringBootApplication 부재),
 * @DataJpaTest / @SpringBootTest가 설정을 찾을 수 있도록 테스트 전용 부트 클래스를 둔다.
 * domain.** 의 @Entity, infrastructure.** 의 @Repository/@Configuration을 함께 스캔한다.
 */
@SpringBootApplication(scanBasePackages = {"com.lifelog.infrastructure", "com.lifelog.domain"})
public class InfrastructureTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(InfrastructureTestApplication.class, args);
    }
}

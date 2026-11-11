package com.som;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// 기본 인메모리 사용자(생성된 비밀번호 로그 출력)를 만들지 않는다. 사용자 조회는 1-auth-consent에서 구현한다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SomApplication {

	public static void main(String[] args) {
		SpringApplication.run(SomApplication.class, args);
	}

}

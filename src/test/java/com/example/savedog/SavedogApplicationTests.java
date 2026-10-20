package com.example.savedog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test 프로필: H2 인메모리 DB를 쓴다. 다른 환경 변수(토큰, CORS, S3, 카카오)는 기본값으로 뜬다.
@SpringBootTest
@ActiveProfiles("test")
class SavedogApplicationTests {

	@Test
	void contextLoads() {
	}

}

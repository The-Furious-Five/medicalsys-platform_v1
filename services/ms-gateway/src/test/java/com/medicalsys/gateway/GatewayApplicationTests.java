package com.medicalsys.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "JWT_SECRET=medicalsys-test-secret-with-at-least-32-bytes")
class GatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.medicalsys.auditoria;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class AuditoriaApplicationTests {

	@Test
	void applicationEntryPointExists() {
		assertDoesNotThrow(() -> Class.forName(AuditoriaApplication.class.getName()));
	}

}

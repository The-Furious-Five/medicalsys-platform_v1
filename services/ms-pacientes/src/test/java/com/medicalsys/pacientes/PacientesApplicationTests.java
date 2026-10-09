package com.medicalsys.pacientes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class PacientesApplicationTests {

	@Test
	void applicationEntryPointExists() {
		assertDoesNotThrow(() -> Class.forName(PacientesApplication.class.getName()));
	}

}

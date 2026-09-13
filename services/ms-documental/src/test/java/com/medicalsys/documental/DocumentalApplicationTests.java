package com.medicalsys.documental;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DocumentalApplicationTests {

	@Test
	void applicationEntryPointExists() {
		assertDoesNotThrow(() -> Class.forName(DocumentalApplication.class.getName()));
	}

}

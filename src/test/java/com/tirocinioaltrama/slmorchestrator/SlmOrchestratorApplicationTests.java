package com.tirocinioaltrama.slmorchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = SlmOrchestratorApplication.class, properties = "ollama.init.enabled=false")
class SlmOrchestratorApplicationTests {

	@Test
	void contextLoads() {
	}

}

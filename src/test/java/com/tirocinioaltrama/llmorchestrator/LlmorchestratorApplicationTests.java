package com.tirocinioaltrama.llmorchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "ollama.init.enabled=false")
class LlmorchestratorApplicationTests {

	@Test
	void contextLoads() {
	}

}

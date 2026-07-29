/*
 * Copyright (c) 2019-2026, dreamlu.net All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dreamlu.mica.ai.ppocr.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrtProviders 单元测试。
 */
class OrtProvidersTest {

	@Test
	void resolve_forceCpu() {
		String[] providers = OrtProviders.resolve(true);
		assertEquals(1, providers.length);
		assertEquals("CPUExecutionProvider", providers[0]);
	}

	@Test
	void resolve_autoNotNull() {
		String[] providers = OrtProviders.resolve(false);
		assertNotNull(providers);
		assertTrue(providers.length >= 1);
		// 结果应是合法的 provider 名称
		for (String p : providers) {
			assertNotNull(p);
			assertFalse(p.isEmpty());
		}
	}
}

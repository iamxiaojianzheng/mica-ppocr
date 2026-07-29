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

package net.dreamlu.mica.ai.ppocr.config;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PPOcrV6Result 单元测试。
 */
class PPOcrV6ResultTest {

	@Test
	void record_basic() {
		int[][] box = {{0, 0}, {100, 0}, {100, 50}, {0, 50}};
		PPOcrV6Result result = new PPOcrV6Result("Hello", 0.95f, box);
		assertEquals("Hello", result.text());
		assertEquals(0.95f, result.score());
		assertArrayEquals(box, result.box());
	}

	@Test
	void boxAsNestedList() {
		int[][] box = {{10, 20}, {30, 20}, {30, 40}, {10, 40}};
		PPOcrV6Result result = new PPOcrV6Result("Test", 0.8f, box);
		List<List<Integer>> nested = result.boxAsNestedList();
		assertEquals(4, nested.size());
		assertEquals(List.of(10, 20), nested.get(0));
		assertEquals(List.of(30, 20), nested.get(1));
		assertEquals(List.of(30, 40), nested.get(2));
		assertEquals(List.of(10, 40), nested.get(3));
	}
}

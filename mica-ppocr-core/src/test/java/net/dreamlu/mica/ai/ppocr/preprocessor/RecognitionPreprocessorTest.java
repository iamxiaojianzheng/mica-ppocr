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

package net.dreamlu.mica.ai.ppocr.preprocessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecognitionPreprocessor 单元测试。
 */
class RecognitionPreprocessorTest {

	@Test
	void constructor_defaults() {
		RecognitionPreprocessor pre = new RecognitionPreprocessor();
		assertNotNull(pre);
	}

	@Test
	void constructor_invalidH() {
		assertThrows(IllegalArgumentException.class, () ->
			new RecognitionPreprocessor(0, 320, 3200));
	}

	@Test
	void constructor_invalidWRange() {
		assertThrows(IllegalArgumentException.class, () ->
			new RecognitionPreprocessor(48, 3200, 320));
	}

	@Test
	void constructor_negativeW() {
		assertThrows(IllegalArgumentException.class, () ->
			new RecognitionPreprocessor(48, -1, 3200));
	}

	@Test
	void call_emptyList() {
		RecognitionPreprocessor pre = new RecognitionPreprocessor();
		RecognitionPreprocessor.Result result = pre.call(java.util.List.of());
		assertEquals(0, result.data().length);
		assertArrayEquals(new int[]{0, 3, 48, 0}, result.shape());
	}
}

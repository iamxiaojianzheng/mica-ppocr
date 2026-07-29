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
 * BoxUtil 单元测试。
 */
class BoxUtilTest {

	@Test
	void sortQuadBoxes_byYthenX() {
		int[][][] boxes = {
			{{100, 50}, {110, 50}, {110, 60}, {100, 60}},  // y=50
			{{0, 10}, {10, 10}, {10, 20}, {0, 20}},         // y=10
			{{50, 50}, {60, 50}, {60, 60}, {50, 60}},       // y=50, x=50
		};
		int[][][] sorted = BoxUtil.sortQuadBoxes(boxes);
		// 排序后: (0,10) → (50,50) → (100,50)
		assertEquals(10, sorted[0][0][1]);    // y=10
		assertEquals(0, sorted[0][0][0]);    // x=0
		assertEquals(50, sorted[1][0][1]);   // y=50
		assertEquals(50, sorted[1][0][0]);   // x=50
		assertEquals(50, sorted[2][0][1]);   // y=50
		assertEquals(100, sorted[2][0][0]);  // x=100
	}

	@Test
	void sortQuadBoxes_singleBox() {
		int[][][] boxes = {{{0, 0}, {10, 0}, {10, 10}, {0, 10}}};
		int[][][] sorted = BoxUtil.sortQuadBoxes(boxes);
		assertEquals(1, sorted.length);
	}

	@Test
	void sortQuadBoxes_empty() {
		int[][][] boxes = {};
		int[][][] sorted = BoxUtil.sortQuadBoxes(boxes);
		assertEquals(0, sorted.length);
	}

	@Test
	void sortQuadBoxes_sameRowReorder() {
		// 同一行（y 差 < 10）按 x 排序
		int[][][] boxes = {
			{{200, 5}, {210, 5}, {210, 15}, {200, 15}},   // y=5, x=200
			{{0, 0}, {10, 0}, {10, 10}, {0, 10}},          // y=0, x=0
			{{100, 8}, {110, 8}, {110, 18}, {100, 18}},   // y=8, x=100
		};
		int[][][] sorted = BoxUtil.sortQuadBoxes(boxes);
		// 按 y 排: (x=0,y=0) → (x=200,y=5) 和 (x=100,y=8) y 差<10 按x: (100,8) → (200,5)
		assertEquals(0, sorted[0][0][0]);
		assertEquals(100, sorted[1][0][0]);
		assertEquals(200, sorted[2][0][0]);
	}

	@Test
	void sortQuadBoxes_doesNotMutateInput() {
		int[][][] boxes = {
			{{100, 50}, {110, 50}, {110, 60}, {100, 60}},
			{{0, 10}, {10, 10}, {10, 20}, {0, 20}},
		};
		BoxUtil.sortQuadBoxes(boxes);
		// 输入不变
		assertEquals(100, boxes[0][0][0]);
		assertEquals(0, boxes[1][0][0]);
	}
}

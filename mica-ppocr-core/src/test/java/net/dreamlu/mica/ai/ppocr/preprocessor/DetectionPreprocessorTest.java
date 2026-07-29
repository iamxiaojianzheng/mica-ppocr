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
 * DetectionPreprocessor 单元测试。
 *
 * <p>resize 对齐规则：Math.round(value / 32) * 32，下限 32。
 */
class DetectionPreprocessorTest {

	@Test
	void computeResizedHW_minLimit_scaleUp() {
		// 短边 100 < limit 640, ratio=6.4
		// h=100*6.4=640 → round(640/32)*32=640
		// w=200*6.4=1280 → round(1280/32)*32=1280
		int[] hw = DetectionPreprocessor.computeResizedHW(100, 200, 640, "min", 4000);
		assertEquals(640, hw[0]);
		assertEquals(1280, hw[1]);
	}

	@Test
	void computeResizedHW_minLimit_noScale() {
		// 短边 800 > limit 640, ratio=1.0
		// h=800 → round(800/32)=25, 25*32=800
		// w=1000 → round(1000/32)=31, 31*32=992
		int[] hw = DetectionPreprocessor.computeResizedHW(800, 1000, 640, "min", 4000);
		assertEquals(800, hw[0]);
		assertEquals(992, hw[1]);
	}

	@Test
	void computeResizedHW_maxLimit_scaleDown() {
		// max(800,2000)=2000 > 1000, ratio=0.5
		// h=800*0.5=400 → round(400/32)=round(12.5)=13, 13*32=416
		// w=2000*0.5=1000 → round(1000/32)=round(31.25)=31, 31*32=992
		int[] hw = DetectionPreprocessor.computeResizedHW(800, 2000, 1000, "max", 4000);
		assertEquals(416, hw[0]);
		assertEquals(992, hw[1]);
	}

	@Test
	void computeResizedHW_maxSideLimit() {
		// min(100,5000)=100 < 640, ratio=6.4 → h=640, w=32000
		// max(640,32000)=32000 > maxSide 2000, ratio2=2000/32000=0.0625
		// h=640*0.0625=40 → round(40/32)=1, max(1*32,32)=32
		// w=32000*0.0625=2000 → round(2000/32)=round(62.5)=63, 63*32=2016
		int[] hw = DetectionPreprocessor.computeResizedHW(100, 5000, 640, "min", 2000);
		assertEquals(32, hw[0]);
		assertEquals(2016, hw[1]);
	}

	@Test
	void computeResizedHW_alignToStride32() {
		// min(100,100)=100 > 64, ratio=1.0
		// h=100 → round(100/32)=round(3.125)=3, 3*32=96
		int[] hw = DetectionPreprocessor.computeResizedHW(100, 100, 64, "min", 4000);
		assertEquals(96, hw[0]);
		assertEquals(96, hw[1]);
		// 验证对齐
		assertEquals(0, hw[0] % 32);
		assertEquals(0, hw[1] % 32);
	}

	@Test
	void computeResizedHW_minLowerBound32() {
		// 极小图像，下限 32
		int[] hw = DetectionPreprocessor.computeResizedHW(1, 1, 64, "min", 4000);
		assertTrue(hw[0] >= 32);
		assertTrue(hw[1] >= 32);
	}

	@Test
	void constructor_invalidLimitType() {
		assertThrows(IllegalArgumentException.class, () ->
			new DetectionPreprocessor(64, "invalid", 4000));
	}

	@Test
	void constructor_zeroLimitSideLen() {
		assertThrows(IllegalArgumentException.class, () ->
			new DetectionPreprocessor(0, "min", 4000));
	}

	@Test
	void constructor_zeroMaxSideLimit() {
		assertThrows(IllegalArgumentException.class, () ->
			new DetectionPreprocessor(64, "min", 0));
	}

	@Test
	void constructor_valid() {
		DetectionPreprocessor pre = new DetectionPreprocessor(64, "min", 4000);
		assertNotNull(pre);
	}
}

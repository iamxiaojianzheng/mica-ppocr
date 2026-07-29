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
 * NdArrayUtils 单元测试。
 */
class NdArrayUtilsTest {

	@Test
	void hwcFlatToNchw_basic() {
		// 2x2 RGB 图像, HWC 顺序: R00 G00 B00 R01 G01 B01 R10 G10 B10 R11 G11 B11
		float[] hwc = {
			1, 2, 3, 4, 5, 6,    // 第0行: pixel(0,0)=[1,2,3], pixel(0,1)=[4,5,6]
			7, 8, 9, 10, 11, 12  // 第1行: pixel(1,0)=[7,8,9], pixel(1,1)=[10,11,12]
		};
		float[] nchw = NdArrayUtils.hwcFlatToNchw(hwc, 2, 2, 3);
		// CHW: R通道=[1,4,7,10], G通道=[2,5,8,11], B通道=[3,6,9,12]
		assertEquals(12, nchw.length);
		assertArrayEquals(new float[]{1, 4, 7, 10}, java.util.Arrays.copyOfRange(nchw, 0, 4));
		assertArrayEquals(new float[]{2, 5, 8, 11}, java.util.Arrays.copyOfRange(nchw, 4, 8));
		assertArrayEquals(new float[]{3, 6, 9, 12}, java.util.Arrays.copyOfRange(nchw, 8, 12));
	}

	@Test
	void argmaxLastAxis_basic() {
		float[][][] x = {
			{{0.1f, 0.5f, 0.4f}, {0.9f, 0.05f, 0.05f}},
			{{0.2f, 0.2f, 0.6f}, {0.3f, 0.4f, 0.3f}}
		};
		int[][] result = NdArrayUtils.argmaxLastAxis(x);
		assertEquals(2, result.length);
		assertArrayEquals(new int[]{1, 0}, result[0]);
		assertArrayEquals(new int[]{2, 1}, result[1]);
	}

	@Test
	void argmaxLastAxis_empty() {
		int[][] result = NdArrayUtils.argmaxLastAxis(new float[0][][]);
		assertEquals(0, result.length);
	}

	@Test
	void maxLastAxis_basic() {
		float[][][] x = {
			{{0.1f, 0.5f, 0.4f}, {0.9f, 0.05f, 0.05f}}
		};
		float[][] result = NdArrayUtils.maxLastAxis(x);
		assertEquals(1, result.length);
		assertArrayEquals(new float[]{0.5f, 0.9f}, result[0]);
	}

	@Test
	void ceilDiv_exact() {
		assertEquals(4, NdArrayUtils.ceilDiv(12, 3));
	}

	@Test
	void ceilDiv_remainder() {
		assertEquals(5, NdArrayUtils.ceilDiv(13, 3));
	}

	@Test
	void ceilDiv_zero() {
		assertEquals(0, NdArrayUtils.ceilDiv(0, 3));
	}

	@Test
	void clamp_int() {
		assertEquals(5, NdArrayUtils.clamp(5, 0, 10));
		assertEquals(0, NdArrayUtils.clamp(-1, 0, 10));
		assertEquals(10, NdArrayUtils.clamp(15, 0, 10));
	}

	@Test
	void clamp_float() {
		assertEquals(0.5f, NdArrayUtils.clamp(0.5f, 0f, 1f));
		assertEquals(0f, NdArrayUtils.clamp(-0.1f, 0f, 1f));
		assertEquals(1f, NdArrayUtils.clamp(1.5f, 0f, 1f));
	}

	@Test
	void clipAll() {
		int[] input = {-1, 5, 15};
		int[] result = NdArrayUtils.clipAll(input, 0, 10);
		assertArrayEquals(new int[]{0, 5, 10}, result);
	}

	@Test
	void roundToInt_basic() {
		assertEquals(3, NdArrayUtils.roundToInt(3.4f));
		assertEquals(4, NdArrayUtils.roundToInt(3.5f));
		assertEquals(-2, NdArrayUtils.roundToInt(-1.6f));
	}

	@Test
	void empty3D() {
		int[][][] result = NdArrayUtils.empty3D();
		assertEquals(0, result.length);
	}
}

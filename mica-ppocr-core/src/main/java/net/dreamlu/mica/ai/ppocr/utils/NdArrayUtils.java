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

import lombok.experimental.UtilityClass;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * 轻量 NDArray 运算工具类。
 *
 * <p>覆盖 ppocrv6_onnx.py 中所需的 numpy 运算：
 * <ul>
 *   <li>HWC ↔ CHW 转置</li>
 *   <li>Mat ↔ flat float[] 转换</li>
 *   <li>沿最后一维的 argmax / max</li>
 *   <li>stack / pad / clip</li>
 * </ul>
 */
@UtilityClass
public final class NdArrayUtils {

	public static float[] hwcFlatToNchw(float[] hwc, int h, int w, int c) {
		float[] chw = new float[c * h * w];
		int hw = h * w;
		for (int i = 0; i < hw; i++) {
			int baseHwc = i * c;
			for (int ch = 0; ch < c; ch++) {
				chw[ch * hw + i] = hwc[baseHwc + ch];
			}
		}
		return chw;
	}

	public static float[] matToFlatHwc(Mat hwc) {
		Mat m = hwc.isContinuous() ? hwc : hwc.clone();
		int h = m.rows();
		int w = m.cols();
		int c = m.channels();
		float[] data = new float[h * w * c];
		m.get(0, 0, data);
		return data;
	}

	public static FloatBuffer toBuffer(float[] flat) {
		return FloatBuffer.wrap(flat);
	}

	public static int[][] argmaxLastAxis(float[][][] x) {
		int b = x.length;
		if (b == 0) {
			return new int[0][];
		}
		int t = x[0].length;
		int[][] idx = new int[b][t];
		for (int i = 0; i < b; i++) {
			float[][] ti = x[i];
			for (int j = 0; j < t; j++) {
				float[] row = ti[j];
				int bestC = 0;
				float bestV = row[0];
				for (int c = 1; c < row.length; c++) {
					float v = row[c];
					if (v > bestV) {
						bestV = v;
						bestC = c;
					}
				}
				idx[i][j] = bestC;
			}
		}
		return idx;
	}

	public static float[][] maxLastAxis(float[][][] x) {
		int b = x.length;
		if (b == 0) {
			return new float[0][];
		}
		int t = x[0].length;
		float[][] m = new float[b][t];
		for (int i = 0; i < b; i++) {
			float[][] ti = x[i];
			for (int j = 0; j < t; j++) {
				float[] row = ti[j];
				float bestV = row[0];
				for (int c = 1; c < row.length; c++) {
					if (row[c] > bestV) {
						bestV = row[c];
					}
				}
				m[i][j] = bestV;
			}
		}
		return m;
	}

	public static float[][][] stack3D(List<float[][]> list) {
		if (list.isEmpty()) {
			return new float[0][][];
		}
		int n = list.size();
		int r = list.get(0).length;
		int c = list.get(0)[0].length;
		float[][][] out = new float[n][r][c];
		for (int i = 0; i < n; i++) {
			float[][] src = list.get(i);
			for (int j = 0; j < r; j++) {
				System.arraycopy(src[j], 0, out[i][j], 0, c);
			}
		}
		return out;
	}

	public static float[][] stack2D(List<float[]> list) {
		if (list.isEmpty()) {
			return new float[0][];
		}
		int n = list.size();
		int cols = list.get(0).length;
		float[][] out = new float[n][cols];
		for (int i = 0; i < n; i++) {
			System.arraycopy(list.get(i), 0, out[i], 0, cols);
		}
		return out;
	}

	public static float[][] padRight(float[][] x, int targetCols) {
		int rows = x.length;
		float[][] out = new float[rows][targetCols];
		for (int i = 0; i < rows; i++) {
			System.arraycopy(x[i], 0, out[i], 0, x[i].length);
		}
		return out;
	}

	public static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}

	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	public static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	public static int[] clipAll(int[] v, int min, int max) {
		int[] out = new int[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = clamp(v[i], min, max);
		}
		return out;
	}

	public static int roundToInt(float v) {
		return Math.round(v);
	}

	public static Mat toFloat32(Mat src) {
		Mat dst = new Mat();
		src.convertTo(dst, CvType.CV_32F);
		return dst;
	}

	public static int[][][] empty3D() {
		return new int[0][][];
	}
}

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
public class NdArrayUtils {

	/**
	 * HWC flat 转 CHW flat。
	 *
	 * @param hwc HWC 顺序的 flat float[]，长度 h*w*c
	 * @param h   高
	 * @param w   宽
	 * @param c   通道数
	 * @return CHW 顺序的 flat float[]，长度 c*h*w
	 */
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

	/**
	 * 将 OpenCV Mat 转为 HWC 顺序的 flat float[]。
	 *
	 * @param hwc 连续的多通道 Mat
	 * @return HWC flat float[]
	 */
	public static float[] matToFlatHwc(Mat hwc) {
		boolean cloned = !hwc.isContinuous();
		Mat m = cloned ? hwc.clone() : hwc;
		try {
			int h = m.rows();
			int w = m.cols();
			int c = m.channels();
			float[] data = new float[h * w * c];
			m.get(0, 0, data);
			return data;
		} finally {
			if (cloned) {
				m.release();
			}
		}
	}

	/**
	 * 将 flat float[] 包装为 FloatBuffer（供 OnnxTensor 使用）。
	 *
	 * @param flat flat float[]
	 * @return 包装后的 FloatBuffer
	 */
	public static FloatBuffer toBuffer(float[] flat) {
		return FloatBuffer.wrap(flat);
	}

	/**
	 * 沿最后一维求 argmax。
	 *
	 * @param x (B, T, C) 三维数组
	 * @return (B, T) 索引数组
	 */
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

	/**
	 * 沿最后一维求最大值。
	 *
	 * @param x (B, T, C) 三维数组
	 * @return (B, T) 最大值数组
	 */
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

	/**
	 * 将 N 个 (R, C) 矩阵堆叠为 (N, R, C) 三维数组。
	 *
	 * @param list (R, C) 矩阵列表
	 * @return (N, R, C) 三维数组
	 */
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

	/**
	 * 将 N 个定长数组堆叠为 (N, C) 二维数组。
	 *
	 * @param list 等长 float[] 列表
	 * @return (N, C) 二维数组
	 */
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

	/**
	 * 右侧补零到指定列数。
	 *
	 * @param x          (N, C) 输入
	 * @param targetCols 目标列数
	 * @return (N, targetCols) 补零后数组
	 */
	public static float[][] padRight(float[][] x, int targetCols) {
		int rows = x.length;
		float[][] out = new float[rows][targetCols];
		for (int i = 0; i < rows; i++) {
			System.arraycopy(x[i], 0, out[i], 0, x[i].length);
		}
		return out;
	}

	/**
	 * 整数向上取整除法。
	 *
	 * @param a 被除数
	 * @param b 除数
	 * @return ceil(a/b)
	 */
	public static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}

	/**
	 * int clamp。
	 *
	 * @param v   输入值
	 * @param min 下界
	 * @param max 上界
	 * @return 截断后的值
	 */
	public static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * float clamp。
	 *
	 * @param v   输入值
	 * @param min 下界
	 * @param max 上界
	 * @return 截断后的值
	 */
	public static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/**
	 * 对数组中每个元素做 clamp。
	 *
	 * @param v   输入数组
	 * @param min 下界
	 * @param max 上界
	 * @return 截断后数组
	 */
	public static int[] clipAll(int[] v, int min, int max) {
		int[] out = new int[v.length];
		for (int i = 0; i < v.length; i++) {
			out[i] = clamp(v[i], min, max);
		}
		return out;
	}

	/**
	 * float 四舍五入到 int。
	 *
	 * @param v 输入值
	 * @return 四舍五入后的 int
	 */
	public static int roundToInt(float v) {
		return Math.round(v);
	}

	/**
	 * OpenCV Mat 转为 float32 类型。
	 *
	 * @param src 源 Mat
	 * @return 转换后的 Mat（CV_32F）
	 */
	public static Mat toFloat32(Mat src) {
		Mat dst = new Mat();
		try {
			src.convertTo(dst, CvType.CV_32F);
			return dst;
		} catch (RuntimeException | Error e) {
			dst.release();
			throw e;
		}
	}

	/**
	 * 创建一个空的三维 int 数组。
	 *
	 * @return new int[0][][]
	 */
	public static int[][][] empty3D() {
		return new int[0][][];
	}
}

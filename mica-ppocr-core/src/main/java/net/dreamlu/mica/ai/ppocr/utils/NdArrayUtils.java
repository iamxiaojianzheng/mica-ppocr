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

/**
 * 轻量 NDArray 运算工具类。
 *
 * <p>覆盖 ppocrv6_onnx.py 中所需的 numpy 运算：
 * <ul>
 *   <li>HWC ↔ CHW 转置</li>
 *   <li>Mat ↔ flat float[] 转换</li>
 *   <li>clamp / ceilDiv</li>
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
}

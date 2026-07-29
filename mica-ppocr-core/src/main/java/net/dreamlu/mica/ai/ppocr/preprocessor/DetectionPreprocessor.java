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

import lombok.ToString;
import net.dreamlu.mica.ai.ppocr.utils.NdArrayUtils;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Set;

/**
 * 检测模型预处理流水线。
 *
 * <p>对应 Python 端的 det 预处理：
 * <ol>
 *   <li>DetResizeForTest.resize_image_type0：按 limitType 缩放，再对齐到 32 倍数</li>
 *   <li>NormalizeImage：(img * (1/255) - mean) / std</li>
 *   <li>HWC → CHW → NCHW（增加 batch 维）</li>
 * </ol>
 *
 * <p>注意：原 PaddleX 代码在 BGR 图像上使用 RGB 顺序的 ImageNet mean/std
 * （值相同但被当成 BGR 通道应用）。这里沿用同样的常量以保证 bit-exact 兼容。
 *
 * <p>输出张量用 flat float[]（长度 C·H'·W'，CHW 顺序），
 * 供 OnnxTensor.createTensor(env, buffer, new long[]{1, C, H, W}) 消费。
 */
@ToString
public final class DetectionPreprocessor {
	public static final Set<String> VALID_LIMIT_TYPES = Set.of("min", "max");
	private static final float SCALE = 1.0f / 255.0f;
	private static final Scalar MEAN = new Scalar(0.485, 0.456, 0.406);
	private static final Scalar STD = new Scalar(0.229, 0.224, 0.225);
	private static final int STRIDE = 32;

	private final int limitSideLen;
	private final String limitType;
	private final int maxSideLimit;

	public DetectionPreprocessor(int limitSideLen, String limitType, int maxSideLimit) {
		if (!VALID_LIMIT_TYPES.contains(limitType)) {
			throw new IllegalArgumentException("limitType must be one of " + VALID_LIMIT_TYPES + ", got '" + limitType + "'");
		}
		if (limitSideLen <= 0) {
			throw new IllegalArgumentException("limitSideLen must be > 0, got " + limitSideLen);
		}
		if (maxSideLimit <= 0) {
			throw new IllegalArgumentException("maxSideLimit must be > 0, got " + maxSideLimit);
		}
		this.limitSideLen = limitSideLen;
		this.limitType = limitType;
		this.maxSideLimit = maxSideLimit;
	}

	public Result call(Mat imgBgr) {
		int srcH = imgBgr.rows();
		int srcW = imgBgr.cols();

		ResizeOutcome ro = resizeImage(imgBgr, srcH, srcW);
		Mat norm = normalize(ro.image);
		int hNew = norm.rows();
		int wNew = norm.cols();
		int c = norm.channels();
		float[] hwcFlat = NdArrayUtils.matToFlatHwc(norm);
		float[] chw = NdArrayUtils.hwcFlatToNchw(hwcFlat, hNew, wNew, c);

		float[] shape = new float[]{
			srcH, srcW,
			(float) ro.ratioH, (float) ro.ratioW
		};
		return new Result(chw, new int[]{1, c, hNew, wNew}, shape);
	}

	private ResizeOutcome resizeImage(Mat img, int h, int w) {
		int limit = limitSideLen;
		double ratio;
		if ("max".equals(limitType)) {
			ratio = Math.max(h, w) > limit ? (double) limit / Math.max(h, w) : 1.0;
		} else {
			ratio = Math.min(h, w) < limit ? (double) limit / Math.min(h, w) : 1.0;
		}

		int rh = (int) (h * ratio);
		int rw = (int) (w * ratio);

		if (Math.max(rh, rw) > maxSideLimit) {
			ratio = (double) maxSideLimit / Math.max(rh, rw);
			rh = (int) (rh * ratio);
			rw = (int) (rw * ratio);
		}

		rh = Math.max(Math.round((float) rh / STRIDE) * STRIDE, STRIDE);
		rw = Math.max(Math.round((float) rw / STRIDE) * STRIDE, STRIDE);

		if (rh == h && rw == w) {
			return new ResizeOutcome(img, 1.0, 1.0);
		}
		Mat resized = new Mat();
		Imgproc.resize(img, resized, new Size(rw, rh), 0, 0, Imgproc.INTER_LINEAR);
		return new ResizeOutcome(resized, (double) rh / h, (double) rw / w);
	}

	private Mat normalize(Mat img) {
		// 重要：Core.multiply/subtract/divide(Mat, Scalar, Mat) 对多通道 Mat
		// 处理有 bug（G/R 通道会变成 +Inf），所以这里先转 float32 Mat，
		// 再用 Java 端循环做归一化 (img * (1/255) - mean) / std。
		Mat f = NdArrayUtils.toFloat32(img);
		float[] hwc = NdArrayUtils.matToFlatHwc(f);
		f.release();
		int n = hwc.length;
		int c = 3;
		int hw = n / c;
		float scale = SCALE;
		float meanB = (float) MEAN.val[0], meanG = (float) MEAN.val[1], meanR = (float) MEAN.val[2];
		float stdB = (float) STD.val[0], stdG = (float) STD.val[1], stdR = (float) STD.val[2];
		for (int i = 0; i < hw; i++) {
			int b = i * c;
			hwc[b] = (hwc[b] * scale - meanB) / stdB;
			hwc[b + 1] = (hwc[b + 1] * scale - meanG) / stdG;
			hwc[b + 2] = (hwc[b + 2] * scale - meanR) / stdR;
		}
		Mat out = new Mat(img.rows(), img.cols(), org.opencv.core.CvType.CV_32FC3);
		out.put(0, 0, hwc);
		return out;
	}

	/**
	 * 静态工具：根据给定 (h, w) 计算 resize 后的实际 (rh, rw)。
	 */
	public static int[] computeResizedHW(int h, int w, int limit, String type, int maxSide) {
		double ratio = "max".equals(type)
			? (Math.max(h, w) > limit ? (double) limit / Math.max(h, w) : 1.0)
			: (Math.min(h, w) < limit ? (double) limit / Math.min(h, w) : 1.0);
		int rh = (int) (h * ratio);
		int rw = (int) (w * ratio);
		if (Math.max(rh, rw) > maxSide) {
			ratio = (double) maxSide / Math.max(rh, rw);
			rh = (int) (rh * ratio);
			rw = (int) (rw * ratio);
		}
		rh = Math.max(Math.round((float) rh / STRIDE) * STRIDE, STRIDE);
		rw = Math.max(Math.round((float) rw / STRIDE) * STRIDE, STRIDE);
		return new int[]{rh, rw};
	}

	public record Result(float[] data, int[] shape, float[] imgShape) {}

	private record ResizeOutcome(Mat image, double ratioH, double ratioW) {}
}

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
import lombok.experimental.Accessors;
import net.dreamlu.mica.ai.ppocr.utils.NdArrayUtils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * 识别模型预处理：resize → normalize → batch padding。
 *
 * <p>对应 Python 端的 rec 预处理：
 * <ol>
 *   <li>按目标高度(48)等比缩放</li>
 *   <li>归一化：(img / 255 - 0.5) / 0.5</li>
 *   <li>batch 内按最大宽度 padding 对齐，输出 NCHW 张量</li>
 * </ol>
 */
@ToString
public final class RecognitionPreprocessor {
	/** 通道数（RGB/BGR）。 */
	public static final int CHANNELS = 3;
	/** 默认目标高度。 */
	public static final int HEIGHT = 48;
	/** 默认最小宽度。 */
	public static final int W_MIN = 320;
	/** 默认最大宽度。 */
	public static final int W_MAX = 3200;

	private final int h;
	private final int wMin;
	private final int wMax;

	/**
	 * 使用默认参数 (h=48, wMin=320, wMax=3200) 创建识别预处理器。
	 */
	public RecognitionPreprocessor() {
		this(HEIGHT, W_MIN, W_MAX);
	}

	/**
	 * 创建识别预处理器。
	 *
	 * @param h    目标高度
	 * @param wMin 最小宽度
	 * @param wMax 最大宽度
	 */
	public RecognitionPreprocessor(int h, int wMin, int wMax) {
		if (h <= 0) {
			throw new IllegalArgumentException("h must be > 0");
		}
		if (wMin <= 0 || wMax <= 0 || wMax < wMin) {
			throw new IllegalArgumentException("invalid wMin/wMax: " + wMin + "/" + wMax);
		}
		this.h = h;
		this.wMin = wMin;
		this.wMax = wMax;
	}

	/**
	 * 执行批量预处理。
	 *
	 * @param imgs 裁剪后的 BGR 文本行图像
	 * @return 预处理结果
	 */
	public Result call(List<Mat> imgs) {
		int n = imgs.size();
		if (n == 0) {
			return new Result(new float[0], new int[]{0, CHANNELS, h, 0});
		}

		// 第一轮：逐图 resize + 归一化，保留 HWC flat 数据
		List<float[]> perImgHwc = new java.util.ArrayList<>(n);
		int[] widths = new int[n];
		int[] actualWs = new int[n];
		for (int i = 0; i < n; i++) {
			Mat img = imgs.get(i);
			int srcH = img.rows();
			int srcW = img.cols();
			double whRatio = Math.max((double) wMin / h, (double) srcW / srcH);
			int targetW = (int) (h * whRatio);

			int actualW;
			Mat resized = new Mat();
			try {
				if (targetW > wMax) {
					Imgproc.resize(img, resized, new Size(wMax, h), 0, 0, Imgproc.INTER_LINEAR);
					actualW = wMax;
					targetW = wMax;
				} else {
					actualW = Math.min(NdArrayUtils.ceilDiv(h * srcW, srcH), targetW);
					Imgproc.resize(img, resized, new Size(actualW, h), 0, 0, Imgproc.INTER_LINEAR);
				}
				widths[i] = targetW;
				actualWs[i] = actualW;

				Mat f = NdArrayUtils.toFloat32(resized);
				float[] hwc;
				try {
					hwc = NdArrayUtils.matToFlatHwc(f);
				} finally {
					f.release();
				}
				// 归一化：(img / 255 - 0.5) / 0.5 = img / 127.5 - 1
				int nPix = hwc.length;
				for (int k = 0; k < nPix; k++) {
					hwc[k] = hwc[k] / 127.5f - 1.0f;
				}
				perImgHwc.add(hwc);
			} finally {
				resized.release();
			}
		}

		// 第二轮：HWC → NCHW + padding，直接写入 data，无中间 float[][] chw
		int maxW = 0;
		for (int w : widths) {
			if (w > maxW) maxW = w;
		}
		int totalSize = n * CHANNELS * h * maxW;
		float[] data = new float[totalSize];
		int chwSize = CHANNELS * h * maxW;
		for (int i = 0; i < n; i++) {
			int actualW = actualWs[i];
			int destBase = i * chwSize;
			float[] hwc = perImgHwc.get(i);
			// HWC → CHW + padding：用 (row, col) 循环避免 div/mod
			for (int c = 0; c < CHANNELS; c++) {
				int cOffset = destBase + c * h * maxW;
				for (int row = 0; row < h; row++) {
					int srcRowBase = row * actualW * CHANNELS + c;
					int dstRowBase = cOffset + row * maxW;
					for (int col = 0; col < actualW; col++) {
						data[dstRowBase + col] = hwc[srcRowBase + col * CHANNELS];
					}
				}
			}
		}

		return new Result(data, new int[]{n, CHANNELS, h, maxW});
	}

	/**
	 * rec 预处理结果。
	 *
	 * @param data  NCHW flat float[] 数据
	 * @param shape [N, C, H, W]
	 */
	@lombok.Value
	@Accessors(fluent = true)
	public static class Result {
		private final float[] data;
		private final int[] shape;
	}
}

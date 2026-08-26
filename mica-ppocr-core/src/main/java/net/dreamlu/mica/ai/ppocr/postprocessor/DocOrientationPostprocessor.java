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

package net.dreamlu.mica.ai.ppocr.postprocessor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 文档方向分类模型后处理：从 4 类 logits 中取出最大概率对应的方向标签。
 *
 * <p>对应官方 PP-LCNet_x1_0_doc_ori 输出层：
 * <ul>
 *   <li>label 0 → 0°（无需旋转）</li>
 *   <li>label 1 → 90°（顺时针）</li>
 *   <li>label 2 → 180°</li>
 *   <li>label 3 → 270°（顺时针）</li>
 * </ul>
 *
 * <p>label 映射来自 PaddleX 官方标签文件：
 * {@code ppcls/utils/PULC_label_list/text_image_orientation_label_list.txt}
 */
@ToString
public final class DocOrientationPostprocessor {

	/**
	 * label 0 = 0°。
	 */
	public static final int ROT_0 = 0;
	/**
	 * label 1 = 90° 顺时针。
	 */
	public static final int ROT_90 = 1;
	/**
	 * label 2 = 180°。
	 */
	public static final int ROT_180 = 2;
	/**
	 * label 3 = 270° 顺时针。
	 */
	public static final int ROT_270 = 3;

	/**
	 * 方向标签到角度的映射（顺时针），与 PaddleX 官方 TAG_LIST 一致。
	 */
	public static final int[] DEGREES = {0, 90, 180, 270};

	private final float confidenceThreshold;

	/**
	 * 创建后处理器。
	 *
	 * @param confidenceThreshold 置信度阈值，低于该值视为方向不确定，返回 0°（不旋转）。
	 *                            PP-OCRv6 默认 0.5，可按需调高到 0.9 减少误判。
	 */
	public DocOrientationPostprocessor(float confidenceThreshold) {
		if (confidenceThreshold < 0.0f || confidenceThreshold > 1.0f) {
			throw new IllegalArgumentException("confidenceThreshold must be in [0, 1], got " + confidenceThreshold);
		}
		this.confidenceThreshold = confidenceThreshold;
	}

	/**
	 * 默认阈值 (0.5)。
	 */
	public DocOrientationPostprocessor() {
		this(0.5f);
	}

	/**
	 * 对 4 类 logits 应用 softmax 后取 argmax。
	 *
	 * @param logits 长度为 4 的 softmax 前 logits（典型 shape: [1,4] 展平后）
	 * @return 分类结果（label + softmax 概率 + 对应角度）
	 */
	public Result call(float[] logits) {
		if (logits == null || logits.length != 4) {
			throw new IllegalArgumentException("logits must be length 4, got " + (logits == null ? "null" : logits.length));
		}
		// softmax：先减最大值提高数值稳定性
		float max = Float.NEGATIVE_INFINITY;
		for (float v : logits) {
			if (v > max) max = v;
		}
		float sum = 0.0f;
		float[] probs = new float[4];
		for (int i = 0; i < 4; i++) {
			probs[i] = (float) Math.exp(logits[i] - max);
			sum += probs[i];
		}
		float invSum = 1.0f / sum;
		for (int i = 0; i < 4; i++) {
			probs[i] *= invSum;
		}

		// argmax
		int bestLabel = 0;
		float bestProb = probs[0];
		for (int i = 1; i < 4; i++) {
			if (probs[i] > bestProb) {
				bestProb = probs[i];
				bestLabel = i;
			}
		}

		// 低置信度降级：按 0° 处理（不旋转）
		if (bestProb < confidenceThreshold) {
			bestLabel = ROT_0;
		}

		return new Result(bestLabel, DEGREES[bestLabel], bestProb, probs);
	}

	/**
	 * 文档方向分类结果。
	 */
	@Getter
	@ToString
	@RequiredArgsConstructor
	@EqualsAndHashCode
	@Accessors(fluent = true)
	public static class Result {
		/**
		 * 类别索引 (0=0° / 1=90° / 2=180° / 3=270°)
		 */
		private final int label;
		/**
		 * 顺时针角度
		 */
		private final int degrees;
		/**
		 * 置信度（softmax 后的概率）
		 */
		private final float score;
		/**
		 * 4 类完整 softmax 概率
		 */
		private final float[] probs;
	}
}

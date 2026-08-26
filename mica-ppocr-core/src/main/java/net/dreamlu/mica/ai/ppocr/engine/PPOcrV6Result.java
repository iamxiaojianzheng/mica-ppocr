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

package net.dreamlu.mica.ai.ppocr.engine;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 单条 OCR 识别结果。
 */
@Getter
@ToString
@EqualsAndHashCode
@Accessors(fluent = true)
@AllArgsConstructor
public class PPOcrV6Result {

	/**
	 * 识别文本
	 */
	private final String text;
	/**
	 * 置信度，范围 [0, 1]
	 */
	private final float score;
	/**
	 * 文本框四顶点，顺序：左上、右上、右下、左下
	 */
	private final int[][] box;
	/**
	 * doc_ori 应用到原图的顺时针旋转角度（0/90/180/270），
	 * 仅当 {@code PPOcrV6Config.useDocOrientationClassify=true}
	 * 且方向分类结果非 0° 时为非 0。值为 0 时 box 坐标系与原图一致；
	 * 非 0 时 box 坐标系相对于 doc_ori 旋转后的图。
	 * 调用方用 {@link PPOcrV6Result#boxInOriginalImg(int, int)} 可获得原始图坐标系下的 box。
	 */
	private final int rotatedDegrees;

	/**
	 * 不旋转的便捷构造器（rotatedDegrees 默认 0）。
	 *
	 * <p>主要为了保留旧调用兼容性：{@code new PPOcrV6Result(text, score, box)}。
	 *
	 * @param text  识别文本
	 * @param score 置信度，范围 [0, 1]
	 * @param box   文本框四顶点，顺序：左上、右上、右下、左下
	 */
	public PPOcrV6Result(String text, float score, int[][] box) {
		this(text, score, box, 0);
	}

	/**
	 * 将文本框四顶点转换为嵌套 List 形式。
	 *
	 * @return [[x0, y0], [x1, y1], [x2, y2], [x3, y3]]
	 */
	public List<List<Integer>> boxAsNestedList() {
		return Collections.unmodifiableList(Arrays.asList(
			Collections.unmodifiableList(Arrays.asList(box[0][0], box[0][1])),
			Collections.unmodifiableList(Arrays.asList(box[1][0], box[1][1])),
			Collections.unmodifiableList(Arrays.asList(box[2][0], box[2][1])),
			Collections.unmodifiableList(Arrays.asList(box[3][0], box[3][1]))
		));
	}

	/**
	 * 将 box 投影回原始图坐标系（doc_ori 旋转之前）。
	 *
	 * <p>doc_ori 旋转的语义是"把图片按顺时针 degrees 度旋转后喂给 OCR 检测"，
	 * 因此 box 坐标系相对于旋转后的图；要回到原始图坐标系，需做**逆向旋转**：
	 * <ul>
	 *   <li>rotatedDegrees = 0：原样返回</li>
	 *   <li>rotatedDegrees = 90：原图 = 顺时针 90° 旋转的图 → 逆向 = 逆时针 90°
	 *       → (x, y) → (h - y, x)，其中 h 为旋转后图高</li>
	 *   <li>rotatedDegrees = 180：(x, y) → (w - x, h - y)</li>
	 *   <li>rotatedDegrees = 270：(x, y) → (y, w - x)，其中 w 为旋转后图宽</li>
	 * </ul>
	 *
	 * @param origW 原始图宽（doc_ori 旋转之前）
	 * @param origH 原始图高（doc_ori 旋转之前）
	 * @return 投影回原始图坐标系后的 box
	 */
	public int[][] boxInOriginalImg(int origW, int origH) {
		if (rotatedDegrees == 0) {
			return box;
		}
		// 旋转后图的尺寸：90/270 时 w,h 互换；180 时不变
		int rotW = (rotatedDegrees == 180) ? origW : origH;
		int rotH = (rotatedDegrees == 180) ? origH : origW;
		int[][] mapped = new int[4][2];
		for (int i = 0; i < 4; i++) {
			int x = box[i][0];
			int y = box[i][1];
			int mx, my;
			// 替代 Java 14+ switch 表达式，保留原始注释说明
			if (rotatedDegrees == 90) {          // 原图被顺时针 90° 后得到 rot 图；逆向 = 逆时针 90°
				mx = rotH - y;
				my = x;
			} else if (rotatedDegrees == 180) {  // 逆向 = 再旋转 180°
				mx = rotW - x;
				my = rotH - y;
			} else if (rotatedDegrees == 270) {  // 逆向 = 顺时针 90°
				mx = y;
				my = rotW - x;
			} else {                              // rotatedDegrees == 0 已短路兜底
				mx = x;
				my = y;
			}
			mapped[i][0] = mx;
			mapped[i][1] = my;
		}
		return mapped;
	}

}

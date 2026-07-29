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

import java.util.List;

/**
 * 单条 OCR 识别结果。
 *
 * @param text  识别文本
 * @param score 置信度，范围 [0, 1]
 * @param box   文本框四顶点，顺序：左上、右上、右下、左下
 */
public record PPOcrV6Result(String text, float score, int[][] box) {

	/**
	 * 将文本框四顶点转换为嵌套 List 形式。
	 *
	 * @return [[x0, y0], [x1, y1], [x2, y2], [x3, y3]]
	 */
	public List<List<Integer>> boxAsNestedList() {
		return List.of(
			List.of(box[0][0], box[0][1]),
			List.of(box[1][0], box[1][1]),
			List.of(box[2][0], box[2][1]),
			List.of(box[3][0], box[3][1])
		);
	}

}

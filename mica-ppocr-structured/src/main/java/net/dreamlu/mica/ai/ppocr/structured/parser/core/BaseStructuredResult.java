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

package net.dreamlu.mica.ai.ppocr.structured.parser.core;

import lombok.Data;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化解析结果基类。
 *
 * <p>所有证件类型的结构化结果（行驶证、身份证、银行卡、驾照等）统一继承本类，
 * 获得以下两个通用能力：
 * <ul>
 *   <li><b>rawResults</b> —— 原始 OCR 识别结果（含每个文字框的文本、置信度、四角坐标），
 *       可用于页面全量复原、调试、自定义可视化；</li>
 *   <li><b>fieldBoxes</b> —— 字段名 → 该字段使用的 OCR 框坐标列表。
 *       一个字段可能由多个 OCR 框拼接/提取而来（例如长地址跨 2~3 行），
 *       因此每个字段映射为一个坐标 list。Map 的 key 即业务字段名（如 "plateNo"、"vin"），
 *       方便调用方在页面上做"高亮字段对应 box"的可视化。</li>
 * </ul>
 *
 * <p>典型可视化流程：
 * <pre>
 * VehicleLicenseResult r = ppocr.parseVehicleLicense(imagePath);
 * // 画所有文字框
 * for (PPOcrV6Result ocr : r.getRawResults()) {
 *     drawBox(ocr.box(), Color.GREEN);
 * }
 * // 高亮车牌字段
 * List&lt;int[][]&gt; plateBoxes = r.getFieldBoxes().get("plateNo");
 * if (plateBoxes != null) {
 *     for (int[][] box : plateBoxes) drawBox(box, Color.RED);
 * }
 * </pre>
 */
@Data
public abstract class BaseStructuredResult {

	/**
	 * 原始 OCR 识别结果（含每个文字框的文本、置信度、四角坐标）。
	 *
	 * <p>用于页面全量复原、调试 OCR 质量、自定义可视化。
	 * 解析器在 {@code parseResults} 入口自动填充。
	 */
	private List<PPOcrV6Result> rawResults = new ArrayList<>();

	/**
	 * 字段名 → 该字段使用的 OCR 框坐标列表。
	 *
	 * <p>每个 {@code int[][]} 表示一个 OCR 检测框的四个角点，按
	 * {@code [4][2]} 排列（外层 4 个点，内层 {@code [x, y]}）。
	 * 角点顺序参考 {@link net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result#box()}。
	 *
	 * <p>示例（行驶证）：
	 * <pre>
	 * fieldBoxes.get("plateNo")  → [[[x0,y0],[x1,y1],[x2,y2],[x3,y3]]]  (外层 List 表示 1 个框)
	 * fieldBoxes.get("vin")      → 同上
	 * </pre>
	 *
	 * <p>一个字段可能由多个 OCR 框拼接/提取而来（例如长地址跨 2~3 行），
	 * 因此 value 是 List 而不是单个 box。
	 *
	 * <p>解析器对有能力定位 box 的字段填充；无法精准定位的字段可能不填充。
	 * 未填充时可通过 {@link #rawResults} 自行根据文本内容做二次匹配。
	 */
	private Map<String, List<int[][]>> fieldBoxes = new HashMap<>();
}

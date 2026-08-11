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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 结构化解析公共工具：标签定位 + 位置匹配 + 正则兜底。
 *
 * <p>适用于左侧标签 + 右侧值 的证件版面（如行驶证、身份证、驾照、营业执照等）：
 * 找到标签框后，在 x 起点位于标签右边缘右侧、y 范围与标签框重叠的候选值框中，
 * 取最靠左（x 最小）的文本作为字段值。
 *
 * <p>支持 OCR 残缺标签匹配：优先取文本包含完整标签的框，没有时退而取标签包含
 * 其文本的框（如"所有人"被识别成"所"）。
 *
 * <h3>两种返回风格</h3>
 * <ul>
 *   <li>{@code matchValue(...) -> String} —— 只返回文本，兼容老代码；</li>
 *   <li>{@code matchValueWithBox(...) -> LabeledMatch} —— 返回文本 + 匹配到的
 *       {@link PPOcrV6Result}（含 box 坐标），供解析器填充
 *       {@link BaseStructuredResult#getFieldBoxes()}，便于页面复原。</li>
 * </ul>
 *
 * <p>本工具类只做"骨架"，不绑定具体业务字段；具体解析器在
 * {@link BaseStructuredParser} 中组合本工具完成结构化输出。
 */
@Slf4j
@UtilityClass
public class LabelMatcher {

	/**
	 * 值框与标签框允许的横向重叠容差（像素），用于容忍边界 1px 相接
	 * （如"发证日期"标签与值框共用 x=2063）。
	 */
	public static final int DEFAULT_RIGHT_OVERLAP_TOLERANCE = 5;

	/**
	 * 字段匹配结果：字段值 + 对应 OCR 结果（含 box 坐标）。
	 *
	 * <p>一个字段可能由多个 OCR 框拼接/提取而来（例如长地址跨多行），
	 * 因此用 {@link #matches()} 承载多个值框（通常只有一个）。
	 */
	public record LabeledMatch(String value, List<PPOcrV6Result> matches) {
		/** 仅文本、无匹配框（兜底场景）。 */
		public static LabeledMatch textOnly(String value) {
			return new LabeledMatch(value, List.of());
		}
		/** 文本 + 单个值框。 */
		public static LabeledMatch of(String value, PPOcrV6Result match) {
			return new LabeledMatch(value, match == null ? List.of() : List.of(match));
		}
		/** 文本 + 多个值框。 */
		public static LabeledMatch of(String value, List<PPOcrV6Result> matches) {
			return new LabeledMatch(value, matches == null ? List.of() : matches);
		}
		public boolean hasValue() {
			return value != null && !value.isEmpty();
		}
	}

	// ==================================================================
	// 无 box 版（兼容老代码）
	// ==================================================================

	public static String matchValue(List<PPOcrV6Result> results, String label) {
		return matchValueWithBox(results, label).value();
	}

	public static String matchValue(List<PPOcrV6Result> results, String label, int rightOverlapTolerance) {
		return matchValueWithBox(results, label, rightOverlapTolerance).value();
	}

	public static String matchValueByCenter(List<PPOcrV6Result> results, String label) {
		return matchValueByCenterWithBox(results, label).value();
	}

	public static String matchValueFromPrefix(List<PPOcrV6Result> results, String label) {
		return matchValueFromPrefixWithBox(results, label).value();
	}

	public static String matchSubstring(List<PPOcrV6Result> results,
										java.util.function.Function<String, String> extractor) {
		return matchSubstringWithBox(results, extractor).value();
	}

	public static String labelOrFallback(String labelValue,
										 List<PPOcrV6Result> results,
										 Pattern pattern,
										 String fieldName,
										 boolean last) {
		LabeledMatch lm = labelOrFallbackWithBox(LabeledMatch.textOnly(labelValue), results, pattern, fieldName, last);
		return lm.value();
	}

	// ==================================================================
	// 带 box 版（推荐新代码使用，便于 fieldBoxes 回填）
	// ==================================================================

	public static LabeledMatch matchValueWithBox(List<PPOcrV6Result> results, String label) {
		return matchValueWithBox(results, label, DEFAULT_RIGHT_OVERLAP_TOLERANCE);
	}

	public static LabeledMatch matchValueWithBox(List<PPOcrV6Result> results, String label, int rightOverlapTolerance) {
		return matchValueByCenterWithBox(results, label);
	}

	public static LabeledMatch matchValueByCenterWithBox(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result labelBox = findLabelBox(results, label);
		if (labelBox == null) {
			log.warn("结构化解析：未找到标签 \"{}\"，该字段置 null", label);
			return LabeledMatch.textOnly(null);
		}

		String labelText = labelBox.text();

		// 合并框场景：返回 null 让 matchValueFromPrefix 兜底
		if (labelText.startsWith(label) && labelText.length() > label.length()) {
			return LabeledMatch.textOnly(null);
		}

		int labelCenterX = (minX(labelBox) + maxX(labelBox)) / 2;
		int labelMinY = minY(labelBox);
		int labelMaxY = maxY(labelBox);

		PPOcrV6Result best = null;
		int bestX = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.matches("[A-Za-z\\s]+")) continue;
			if (!text.equals(label) && text.length() < label.length() && label.contains(text)) continue;
			int x0 = minX(r);
			int rCenterX = (x0 + maxX(r)) / 2;
			if (rCenterX <= labelCenterX) continue;
			if (maxY(r) < labelMinY || minY(r) > labelMaxY) continue;
			if (x0 < bestX) {
				bestX = x0;
				best = r;
			}
		}
		if (best == null) {
			log.warn("结构化解析：标签 \"{}\" 未匹配到值框，该字段置 null", label);
			return LabeledMatch.textOnly(null);
		}
		return LabeledMatch.of(best.text(), best);
	}

	public static LabeledMatch matchValueFromPrefixWithBox(List<PPOcrV6Result> results, String label) {
		LabeledMatch m = matchValueByCenterWithBox(results, label);
		if (m.hasValue()) return m;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith(label) && text.length() > label.length()) {
				String stripped = text.substring(label.length());
				if (stripped.trim().isEmpty()) continue;
				log.info("结构化解析：标签 \"{}\" 从合并框 \"{}\" 剥出值 \"{}\"", label, text, stripped);
				return LabeledMatch.of(stripped, r);
			}
		}
		return LabeledMatch.textOnly(null);
	}

	public static LabeledMatch matchPatternWithBox(List<PPOcrV6Result> results, Pattern pattern, boolean last) {
		PPOcrV6Result hit = null;
		for (PPOcrV6Result r : results) {
			if (pattern.matcher(r.text()).matches()) {
				hit = r;
				if (!last) break;
			}
		}
		return hit == null ? LabeledMatch.textOnly(null) : LabeledMatch.of(hit.text(), hit);
	}

	/** 保留 Predicate 版（无 box 版）兼容。 */
	public static String matchPattern(List<PPOcrV6Result> results, Pattern pattern, boolean last) {
		return matchPatternWithBox(results, pattern, last).value();
	}

	public static String matchPattern(List<PPOcrV6Result> results, Predicate<String> predicate, boolean last) {
		String hit = null;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (predicate.test(text)) {
				hit = text;
				if (!last) break;
			}
		}
		return hit;
	}

	public static LabeledMatch matchSubstringWithBox(List<PPOcrV6Result> results,
													 java.util.function.Function<String, String> extractor) {
		for (PPOcrV6Result r : results) {
			String hit = extractor.apply(r.text());
			if (hit != null) {
				return LabeledMatch.of(hit, r);
			}
		}
		return LabeledMatch.textOnly(null);
	}

	public static LabeledMatch labelOrFallbackWithBox(LabeledMatch labelMatch,
													  List<PPOcrV6Result> results,
													  Pattern pattern,
													  String fieldName,
													  boolean last) {
		if (labelMatch.hasValue()) {
			if (pattern.matcher(labelMatch.value()).matches()) {
				return labelMatch;
			}
			log.warn("结构化解析：{} 位置匹配 \"{}\" 格式异常，改走正则兜底", fieldName, labelMatch.value());
		}
		LabeledMatch fallback = matchPatternWithBox(results, pattern, last);
		if (fallback.hasValue()) {
			log.info("结构化解析：{} 正则兜底命中 \"{}\"", fieldName, fallback.value());
		}
		return fallback;
	}

	/**
	 * 辅助：把 {@link LabeledMatch} 回填到结构化结果的 fieldBoxes 中。
	 */
	public static void applyFieldBox(BaseStructuredResult result, String fieldName, LabeledMatch match) {
		if (result == null || fieldName == null || match == null || match.matches().isEmpty()) {
			return;
		}
		List<int[][]> boxes = new ArrayList<>(match.matches().size());
		for (PPOcrV6Result r : match.matches()) {
			if (r != null && r.box() != null) {
				boxes.add(r.box());
			}
		}
		if (!boxes.isEmpty()) {
			result.getFieldBoxes().put(fieldName, boxes);
		}
	}

	// ==================================================================
	// 其余公开方法（不变）
	// ==================================================================

	public static PPOcrV6Result findLabelBox(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result exactBest = null;
		PPOcrV6Result prefixBest = null;
		PPOcrV6Result fragmentBest = null;
		int prefixBestLen = -1;
		int fragmentBestLen = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) continue;
			if (text.equals(label)) {
				exactBest = r;
			} else if (text.startsWith(label)) {
				if (text.length() > prefixBestLen) {
					prefixBestLen = text.length();
					prefixBest = r;
				}
			} else if (label.contains(text)) {
				if (text.length() > fragmentBestLen) {
					fragmentBestLen = text.length();
					fragmentBest = r;
				}
			}
		}
		if (exactBest != null) return exactBest;
		if (prefixBest != null) return prefixBest;
		if (fragmentBest != null) {
			log.warn("[DEBUG-FIND] label='{}' fragment hit: text='{}' (fragment len={})", label, fragmentBest.text(), fragmentBestLen);
		}
		return fragmentBest;
	}

	public static int minX(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) min = Math.min(min, p[0]);
		return min;
	}
	public static int maxX(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) max = Math.max(max, p[0]);
		return max;
	}
	public static int minY(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) min = Math.min(min, p[1]);
		return min;
	}
	public static int maxY(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) max = Math.max(max, p[1]);
		return max;
	}
}

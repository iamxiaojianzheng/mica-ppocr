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
import java.util.Comparator;
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
 * <p><b>两种返回风格：</b>
 * <ul>
 *   <li>{@code matchValue(...) -> String} —— 只返回文本，兼容老代码；</li>
 *   <li>{@code matchValueWithBox(...) -> LabeledMatch} —— 返回文本 + 匹配到的
 *       {@link PPOcrV6Result}（含 box 坐标），供解析器填充
 *       {@code BaseStructuredResult#getFieldBoxes()}，便于页面复原。</li>
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
	 *
	 * @param value   字段值
	 * @param matches 匹配到的 OCR 结果（含 box 坐标）
	 */
	public record LabeledMatch(String value, List<PPOcrV6Result> matches) {
		/**
		 * 仅文本、无匹配框（兜底场景）。
		 *
		 * @param value 字段值（可为空）
		 * @return 文本 LabeledMatch
		 */
		public static LabeledMatch textOnly(String value) {
			return new LabeledMatch(value, List.of());
		}

		/**
		 * 文本 + 单个值框。
		 *
		 * @param value 字段值
		 * @param match 值框对应的 OCR 结果；null 时回退为空 list
		 * @return 单值框 LabeledMatch
		 */
		public static LabeledMatch of(String value, PPOcrV6Result match) {
			return new LabeledMatch(value, match == null ? List.of() : List.of(match));
		}

		/**
		 * 文本 + 多个值框（跨行字段如长地址）。
		 *
		 * @param value   字段值
		 * @param matches 值框 OCR 结果列表；null 时回退为空 list
		 * @return 多值框 LabeledMatch
		 */
		public static LabeledMatch of(String value, List<PPOcrV6Result> matches) {
			return new LabeledMatch(value, matches == null ? List.of() : matches);
		}

		/**
		 * 判断是否存在非空字段值。
		 *
		 * @return true 表示 value 非 null 且非空字符串
		 */
		public boolean hasValue() {
			return value != null && !value.isEmpty();
		}
	}

	// ==================================================================
	// 无 box 版（兼容老代码）
	// ==================================================================

	/**
	 * 兼容老代码：取字段文本，不返回匹配框。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签（如 "号牌号码"）
	 * @return 字段值；未匹配到时返回 null
	 */
	public static String matchValue(List<PPOcrV6Result> results, String label) {
		return matchValueWithBox(results, label).value();
	}

	/**
	 * 兼容老代码：取字段文本，容忍标签与值框横向重叠像素。
	 *
	 * @param results               OCR 识别结果列表
	 * @param label                 字段标签
	 * @param rightOverlapTolerance 横向重叠容差（像素）
	 * @return 字段值；未匹配到时返回 null
	 */
	public static String matchValue(List<PPOcrV6Result> results, String label, int rightOverlapTolerance) {
		return matchValueWithBox(results, label, rightOverlapTolerance).value();
	}

	/**
	 * 兼容老代码：按"标签右侧 + y 重叠 + 最左"策略取值。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签
	 * @return 字段值；未匹配到时返回 null
	 */
	public static String matchValueByCenter(List<PPOcrV6Result> results, String label) {
		return matchValueByCenterWithBox(results, label).value();
	}

	/**
	 * 兼容老代码：标签和值在同一 OCR 框里时，从合并框剥出值。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签
	 * @return 字段值；未匹配到时返回 null
	 */
	public static String matchValueFromPrefix(List<PPOcrV6Result> results, String label) {
		return matchValueFromPrefixWithBox(results, label).value();
	}

	/**
	 * 兼容老代码：用提取器在所有 OCR 文本上试匹配，返回首个非空结果。
	 *
	 * @param results   OCR 识别结果列表
	 * @param extractor 文本→值的提取函数
	 * @return 提取结果；无匹配时返回 null
	 */
	public static String matchSubstring(List<PPOcrV6Result> results,
										java.util.function.Function<String, String> extractor) {
		return matchSubstringWithBox(results, extractor).value();
	}

	/**
	 * 兼容老代码：标签匹配优先，否则按正则兜底。
	 *
	 * @param labelValue 标签位置匹配得到的值（可为 null）
	 * @param results    OCR 识别结果列表
	 * @param pattern    兜底正则
	 * @param fieldName  字段名（日志用）
	 * @param last       true=取最后一个匹配；false=取首个匹配
	 * @return 最终字段值；无匹配时返回 null
	 */
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

	/**
	 * 取字段值 + 匹配框，使用默认横向重叠容差 {@link #DEFAULT_RIGHT_OVERLAP_TOLERANCE}。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签
	 * @return 字段值 + 值框
	 */
	public static LabeledMatch matchValueWithBox(List<PPOcrV6Result> results, String label) {
		return matchValueWithBox(results, label, DEFAULT_RIGHT_OVERLAP_TOLERANCE);
	}

	/**
	 * 取字段值 + 匹配框，自定义横向重叠容差。
	 *
	 * @param results               OCR 识别结果列表
	 * @param label                 字段标签
	 * @param rightOverlapTolerance 横向重叠容差（像素）
	 * @return 字段值 + 值框
	 */
	public static LabeledMatch matchValueWithBox(List<PPOcrV6Result> results, String label, int rightOverlapTolerance) {
		return matchValueByCenterWithBox(results, label);
	}

	/**
	 * 按"标签右侧 + y 重叠 + 最左"策略取字段值 + 匹配框。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签
	 * @return 字段值 + 值框；未匹配到时返回仅含 null value 的 LabeledMatch
	 */
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

	/**
	 * 在 {@link #matchValueByCenterWithBox} 失败时，从"以 label 开头的合并框"剥出值。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签
	 * @return 字段值 + 值框；未匹配到时返回仅含 null value 的 LabeledMatch
	 */
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

	/**
	 * 用正则匹配 OCR 文本，返回首个/最后一个匹配项。
	 *
	 * @param results OCR 识别结果列表
	 * @param pattern 文本匹配正则
	 * @param last    true=取最后一个匹配；false=取首个匹配
	 * @return 字段值 + 值框；未匹配到时返回仅含 null value 的 LabeledMatch
	 */
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

	/**
	 * 兼容老代码：用正则在 OCR 文本上匹配，返回首个/最后一个命中文本。
	 *
	 * @param results OCR 识别结果列表
	 * @param pattern 文本匹配正则
	 * @param last    true=取最后一个匹配；false=取首个匹配
	 * @return 命中文本；无匹配时返回 null
	 */
	public static String matchPattern(List<PPOcrV6Result> results, Pattern pattern, boolean last) {
		return matchPatternWithBox(results, pattern, last).value();
	}

	/**
	 * 用 Predicate 在 OCR 文本上筛选，返回首个/最后一个命中文本。
	 *
	 * @param results   OCR 识别结果列表
	 * @param predicate 文本命中判断
	 * @param last      true=取最后一个命中；false=取首个命中
	 * @return 命中文本；无匹配时返回 null
	 */
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

	/**
	 * 用提取器在所有 OCR 文本上试匹配，返回首个非空提取结果。
	 *
	 * @param results   OCR 识别结果列表
	 * @param extractor 文本→值的提取函数
	 * @return 提取结果 + 命中的值框；无匹配时返回仅含 null value 的 LabeledMatch
	 */
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

	/**
	 * 标签位置匹配优先，若 value 缺失或不匹配正则则按正则兜底。
	 *
	 * @param labelMatch 标签位置匹配结果（可能 value 为 null）
	 * @param results    OCR 识别结果列表（兜底时遍历）
	 * @param pattern    兜底正则
	 * @param fieldName  字段名（日志用）
	 * @param last       true=兜底时取最后一个匹配；false=取首个匹配
	 * @return 最终 LabeledMatch
	 */
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
	 *
	 * @param result    结构化结果对象
	 * @param fieldName 字段名（如 "plateNo"）
	 * @param match     字段匹配结果（含值框列表）
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

	/**
	 * 在 OCR 结果中定位字段标签框，兼容 OCR 残缺场景。
	 *
	 * <p>匹配优先级：完整等于 &gt; 以 label 开头（最长）&gt; label 包含文本（最长）；
	 * 前两者未命中时回退到第三种并打印 WARN 日志。
	 *
	 * @param results OCR 识别结果列表
	 * @param label   字段标签（如 "号牌号码"）
	 * @return 标签框对应的 OCR 结果；无匹配时返回 null
	 */
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

	/**
	 * 字段标签框定位的"干净版"：在 {@link #findLabelBox} 基础上拒绝被其他已知字段关键字
	 * 污染的 fragment（如营业执照 OCR 把"名称"和"类型"合并成"名类"——返回 null 而不是
	 * 错误命中"名"/"类" fragment）。
	 *
	 * <p>判定规则：
	 * <ol>
	 *   <li>完整等于 label → 接受；</li>
	 *   <li>以 label 开头 → 接受；</li>
	 *   <li>label 包含 text 且 text 长度 = 1（单字 fragment "名"/"称"/"类"/"型"/"住"/"所"）→ 接受；</li>
	 *   <li>label 包含 text 但 text 长度 ≥ 2 → 拒绝（噪声合并框，应由调用方做合并框剥值）。</li>
	 * </ol>
	 *
	 * @param results      OCR 识别结果列表
	 * @param label        字段标签（如 "住所"）
	 * @param noiseLabels  其他已知字段标签集合（如 ["名称","类型","注册资本",...])，
	 *                     fragment 文本如果包含其中任一标签视为污染并拒绝
	 * @return 干净标签框；无匹配时返回 null
	 */
	public static PPOcrV6Result findCleanLabelBox(List<PPOcrV6Result> results,
												  String label,
												  java.util.Set<String> noiseLabels) {
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
				// 拒绝被其他字段标签关键字污染的 fragment
				if (noiseLabels != null) {
					boolean polluted = false;
					for (String noise : noiseLabels) {
						if (!noise.equals(label) && text.contains(noise)) {
							polluted = true;
							break;
						}
					}
					if (polluted) continue;
				}
				// fragment 长度 ≥ 2 且非单字 fragment → 拒绝（视为合并框，由调用方剥值）
				if (text.length() >= 2) continue;
				if (text.length() > fragmentBestLen) {
					fragmentBestLen = text.length();
					fragmentBest = r;
				}
			}
		}
		if (exactBest != null) return exactBest;
		if (prefixBest != null) return prefixBest;
		if (fragmentBest != null) {
			log.debug("[DEBUG-FIND-CLEAN] label='{}' fragment hit: text='{}'", label, fragmentBest.text());
		}
		return fragmentBest;
	}

	/**
	 * 取标签右侧 y 重叠的所有候选框，按 y 升序拼接成多行值。
	 *
	 * <p>适用于经营范围 / 住所 / 营业期限等跨多行字段。规则：
	 * <ul>
	 *   <li>值框中心 x &gt; 标签中心 x；</li>
	 *   <li>值框 y 与标签 y 有重叠（允许下方延伸一行）；</li>
	 *   <li>拼接前按 y 升序排序，多行用空格分隔。</li>
	 * </ul>
	 *
	 * @param labelBox      标签框
	 * @param results       OCR 结果列表
	 * @param skipTexts     需要排除的文本（防止把其他标签 fragment 拼进来）
	 * @return 多行拼接值；无候选时返回 null
	 */
	public static String collectMultiLineRight(PPOcrV6Result labelBox,
											   List<PPOcrV6Result> results,
											   java.util.Set<String> skipTexts) {
		if (labelBox == null) return null;
		int labelCenterX = (minX(labelBox) + maxX(labelBox)) / 2;
		int labelMinY = minY(labelBox);
		int labelMaxY = maxY(labelBox);
		List<PPOcrV6Result> candidates = new ArrayList<>();
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.isEmpty()) continue;
			if (skipTexts != null && skipTexts.contains(text)) continue;
			int x0 = minX(r);
			int rCenterX = (x0 + maxX(r)) / 2;
			if (rCenterX <= labelCenterX) continue;
			int rMinY = minY(r);
			int rMaxY = maxY(r);
			// y 重叠 + 下方允许延伸一行
			int oneLine = labelMaxY - labelMinY;
			if (rMaxY < labelMinY || rMinY > labelMaxY + oneLine) continue;
			candidates.add(r);
		}
		candidates.sort(Comparator.comparingInt(LabelMatcher::minY));
		StringBuilder sb = new StringBuilder();
		for (PPOcrV6Result r : candidates) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(r.text());
		}
		String result = sb.toString().trim();
		return result.isEmpty() ? null : result;
	}

	/**
	 * 取 OCR 框四点的最小 x 坐标。
	 *
	 * @param r OCR 识别结果
	 * @return 最小 x
	 */
	public static int minX(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) min = Math.min(min, p[0]);
		return min;
	}

	/**
	 * 取 OCR 框四点的最大 x 坐标。
	 *
	 * @param r OCR 识别结果
	 * @return 最大 x
	 */
	public static int maxX(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) max = Math.max(max, p[0]);
		return max;
	}

	/**
	 * 取 OCR 框四点的最小 y 坐标。
	 *
	 * @param r OCR 识别结果
	 * @return 最小 y
	 */
	public static int minY(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) min = Math.min(min, p[1]);
		return min;
	}

	/**
	 * 取 OCR 框四点的最大 y 坐标。
	 *
	 * @param r OCR 识别结果
	 * @return 最大 y
	 */
	public static int maxY(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) max = Math.max(max, p[1]);
		return max;
	}
}

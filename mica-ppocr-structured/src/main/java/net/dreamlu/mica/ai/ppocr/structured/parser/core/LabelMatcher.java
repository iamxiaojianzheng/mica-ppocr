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
	 * 按标签匹配值框文本。
	 *
	 * <p>标签查找支持 OCR 残缺标签：优先取文本包含完整标签的框，没有时退而取
	 * 标签包含其文本的框，取其中文本最长者（最接近完整标签）。
	 *
	 * @param results  OCR 结果列表
	 * @param label    标签文本（如"号牌号码"）
	 * @return 匹配到的值框文本，找不到返回 null
	 */
	public static String matchValue(List<PPOcrV6Result> results, String label) {
		return matchValue(results, label, DEFAULT_RIGHT_OVERLAP_TOLERANCE);
	}

	/**
	 * 按标签匹配值框文本，允许自定义横向重叠容差。
	 *
	 * @param results               OCR 结果列表
	 * @param label                 标签文本
	 * @param rightOverlapTolerance 横向重叠容差（像素）
	 * @return 匹配到的值框文本，找不到返回 null
	 */
	public static String matchValue(List<PPOcrV6Result> results, String label, int rightOverlapTolerance) {
		// 默认按"中心点"判定（兼容 OCR 边界框与标签框部分重叠场景）。
		// 参数 rightOverlapTolerance 保留仅为 API 兼容，不再使用。
		return matchValueByCenter(results, label);
	}

	/**
	 * 按标签匹配值框文本（值框中心 x 必须落在标签中心 x 右侧）。
	 *
	 * <p>与 {@link #matchValue} 的区别：本方法对 OCR 把"标签+值"识别成
	 * 单一文本框（text 包含 label 前缀）的场景兼容，会跳过这类框；
	 * 同时要求"值框的视觉中心"在标签框右侧（而非仅"左边缘在右侧"），
	 * 避免因标签框较宽（中文 4 字标签的边界框 x1 远大于 x0）而把
	 * 相邻同一行的值框误判为"在标签框内"。
	 *
	 * @param results OCR 结果列表
	 * @param label   标签文本
	 * @return 匹配到的值框文本
	 */
	public static String matchValueByCenter(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result labelBox = findLabelBox(results, label);
		if (labelBox == null) {
			log.warn("结构化解析：未找到标签 \"{}\"，该字段置 null", label);
			return null;
		}

		String labelText = labelBox.text();

		// 合并框场景（OCR 把"标签+值"识别成单一文本框）：
		// 此方法不擅长处理合并框（无法从合并框边界框准确拆分 label 与 value 的位置），
		// 直接返回 null 让 matchValueFromPrefix 兜底（按 text.startsWith(label) 剥前缀）。
		if (labelText.startsWith(label) && labelText.length() > label.length()) {
			return null;
		}

		// 非合并框：使用完整 labelBox 的几何中心
		int labelCenterX = (minX(labelBox) + maxX(labelBox)) / 2;
		int labelMinY = minY(labelBox);
		int labelMaxY = maxY(labelBox);

		String best = null;
		int bestX = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			if (r == labelBox) {
				continue;
			}
			String text = r.text();
			// 跳过纯英文的标签文本（如"Date of Birth"/"Name"/"Sex" 等）
			if (text.matches("[A-Za-z\\s]+")) {
				continue;
			}
			// 跳过标签的其他残缺片段（如"所有人"被识别成"所"+"人"，跳过"人"）
			if (!text.equals(label) && text.length() < label.length() && label.contains(text)) {
				continue;
			}
			int x0 = minX(r);
			int rCenterX = (x0 + maxX(r)) / 2;
			// 值框中心必须在标签中心右侧
			if (rCenterX <= labelCenterX) {
				continue;
			}
			// y 范围必须与标签框重叠（同一行）
			if (maxY(r) < labelMinY || minY(r) > labelMaxY) {
				continue;
			}
			if (x0 < bestX) {
				bestX = x0;
				best = text;
			}
		}
		if (best == null) {
			log.warn("结构化解析：标签 \"{}\" 未匹配到值框，该字段置 null", label);
		}
		return best;
	}

	/**
	 * 查找标签框，支持 OCR 残缺标签。
	 *
	 * <p>取文本最长者（最接近完整标签），找不到返回 null。
	 *
	 * @param results OCR 结果列表
	 * @param label   标签文本
	 * @return 标签框；找不到返回 null
	 */
	public static PPOcrV6Result findLabelBox(List<PPOcrV6Result> results, String label) {
		// 优先级 1：text 等于 label（纯标签框）
		// 优先级 2：text 以 label 开头（标签+值合并框；用其左侧部分作为标签定位基准）
		// 优先级 3：label 包含 text（残缺标签框）
		PPOcrV6Result exactBest = null;
		PPOcrV6Result prefixBest = null;
		PPOcrV6Result fragmentBest = null;
		int prefixBestLen = -1;
		int fragmentBestLen = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) {
				continue;
			}
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
		if (exactBest != null) {
			return exactBest;
		}
		if (prefixBest != null) {
			return prefixBest;
		}
		if (fragmentBest != null) {
			log.warn("[DEBUG-FIND] label='{}' fragment hit: text='{}' (fragment len={})", label, fragmentBest.text(), fragmentBestLen);
		}
		return fragmentBest;
	}

	/**
	 * 按内容特征正则扫描全部结果。
	 *
	 * @param results OCR 结果列表
	 * @param pattern 特征正则（整串匹配）
	 * @param last    取最后一个匹配（true）还是第一个（false）
	 * @return 匹配文本，找不到返回 null
	 */
	public static String matchPattern(List<PPOcrV6Result> results, Pattern pattern, boolean last) {
		String hit = null;
		for (PPOcrV6Result r : results) {
			if (pattern.matcher(r.text()).matches()) {
				hit = r.text();
				if (!last) {
					break;
				}
			}
		}
		return hit;
	}

	/**
	 * 按内容特征正则扫描全部结果，支持自定义筛选器。
	 *
	 * <p>典型用法：在正则匹配之外再叠加业务筛选，例如"必须是 17 位且不含 I/O/Q"。
	 *
	 * @param results   OCR 结果列表
	 * @param predicate 命中后还需满足的筛选器
	 * @param last      取最后一个匹配（true）还是第一个（false）
	 * @return 通过筛选的匹配文本，找不到返回 null
	 */
	public static String matchPattern(List<PPOcrV6Result> results, Predicate<String> predicate, boolean last) {
		String hit = null;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (predicate.test(text)) {
				hit = text;
				if (!last) {
					break;
				}
			}
		}
		return hit;
	}

	/**
	 * 处理"标签+值合并"识别场景：OCR 把"性别男"识别成单一文本框时，
	 * 剥掉标签前缀，剩下的部分就是字段值。
	 *
	 * <p>本方法先尝试 {@link #matchValueByCenter}（标准标签定位 + 位置匹配）；
	 * 若返回 null，再扫描所有框，从包含标签前缀的合并框中剥出值。
	 *
	 * @param results OCR 结果列表
	 * @param label   标签文本
	 * @return 字段值；标签不存在且无合并框可剥时返回 null
	 */
	public static String matchValueFromPrefix(List<PPOcrV6Result> results, String label) {
		// 1) 优先走标准标签定位（标签和值是独立 OCR 框）
		String value = matchValueByCenter(results, label);
		if (value != null) {
			return value;
		}
		// 2) 兜底：扫描合并框（OCR 把"标签+值"识别成单一文本框）
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith(label) && text.length() > label.length()) {
				String stripped = text.substring(label.length());
				// 跳过剥出来为空或纯空白
				if (stripped.trim().isEmpty()) {
					continue;
				}
				log.info("结构化解析：标签 \"{}\" 从合并框 \"{}\" 剥出值 \"{}\"", label, text, stripped);
				return stripped;
			}
		}
		return null;
	}

	/**
	 * 在文本中查找满足 predicate 的子串，返回第一个匹配的"提取结果"。
	 *
	 * <p>用于 OCR 噪声场景下从带噪文本中提取结构化子串（例如带前导点号的 VIN）。
	 * extractor 应自行处理 find/matches，并返回想要抽取的子串。
	 *
	 * @param results   OCR 结果列表
	 * @param extractor 子串提取器（输入为 OCR 文本，输出为抽取结果或 null）
	 * @return 第一个非空的提取结果，找不到返回 null
	 */
	public static String matchSubstring(List<PPOcrV6Result> results,
										java.util.function.Function<String, String> extractor) {
		for (PPOcrV6Result r : results) {
			String hit = extractor.apply(r.text());
			if (hit != null) {
				return hit;
			}
		}
		return null;
	}

	/**
	 * 标签定位优先，结果经正则校验；不合法时改走正则兜底。
	 *
	 * @param labelValue 标签定位结果（可能为 null）
	 * @param results    OCR 结果列表
	 * @param pattern    格式校验正则
	 * @param fieldName  字段名（日志用）
	 * @param last       正则兜底时取最后一个匹配（true）还是第一个（false）
	 * @return 最终字段值
	 */
	public static String labelOrFallback(String labelValue,
										 List<PPOcrV6Result> results,
										 Pattern pattern,
										 String fieldName,
										 boolean last) {
		if (labelValue != null) {
			if (pattern.matcher(labelValue).matches()) {
				return labelValue; // 标签定位 + 格式校验通过
			}
			log.warn("结构化解析：{} 位置匹配 \"{}\" 格式异常，改走正则兜底", fieldName, labelValue);
		}
		String fallback = matchPattern(results, pattern, last);
		if (fallback != null) {
			log.info("结构化解析：{} 正则兜底命中 \"{}\"", fieldName, fallback);
		}
		return fallback;
	}

	// ----- 几何工具：框四顶点 min/max -----

	public static int minX(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) {
			min = Math.min(min, p[0]);
		}
		return min;
	}

	public static int maxX(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) {
			max = Math.max(max, p[0]);
		}
		return max;
	}

	public static int minY(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) {
			min = Math.min(min, p[1]);
		}
		return min;
	}

	public static int maxY(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) {
			max = Math.max(max, p[1]);
		}
		return max;
	}
}

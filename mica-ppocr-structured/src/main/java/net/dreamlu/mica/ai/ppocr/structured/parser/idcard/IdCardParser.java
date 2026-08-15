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

package net.dreamlu.mica.ai.ppocr.structured.parser.idcard;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 身份证 OCR 结构化解析器（正反面合一）。
 *
 * <p>策略概览：
 * <ul>
 *   <li><b>版面判定</b>：扫描 OCR 框是否存在 "公民身份号码" 或 "姓名" 标签 → 正面；
 *       若存在 "签发机关" 或 "有效期限" 标签 → 反面；两者都没出现 → UNKNOWN。</li>
 *   <li><b>正面字段</b>：姓名/性别/民族/出生日期/住址 按标签定位；
 *       公民身份号码用 18 位正则兜底（应对 OCR 残缺 "公民身份号码" 标签）。</li>
 *   <li><b>反面字段</b>：签发机关 按 "签发机关" 标签定位；
 *       有效期限 从 "YYYY.MM.DD[-YYYY.MM.DD]" 文本中按 "." 分隔符切出起止。</li>
 * </ul>
 */
@Slf4j
public class IdCardParser extends BaseStructuredParser<IdCardResult> {

	/**
	 * 正面字段标签（合并框切分用）：OCR 可能把 "性别男民族汉" 双标签连写进同一框。
	 */
	private static final String[] FRONT_LABELS = {"姓名", "性别", "民族", "出生", "住址", "公民身份号码"};
	/**
	 * 公民身份号码：18 位（末位 X 允许）。
	 */
	private static final Pattern ID_NUMBER_PATTERN = Pattern.compile("[0-9X]{18}");
	/**
	 * 出生日期：yyyy 年 MM 月 dd 日（容忍空格、可选"日"字）。
	 */
	private static final Pattern BIRTH_DATE_PATTERN = Pattern.compile(
		"\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日?");
	/**
	 * 有效期限格式：YYYY.MM.DD[-YYYY.MM.DD] 或长期（"长期"）。
	 */
	private static final Pattern VALID_TERM_PATTERN = Pattern.compile(
		"\\d{4}\\.\\d{2}\\.\\d{2}(-\\d{4}\\.\\d{2}\\.\\d{2})?|长期");

	/**
	 * 构造身份证解析器，绑定推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎；可为 null（仅在仅调用 {@link #parseResults(List)} 时）
	 */
	public IdCardParser(PPOcrV6Engine engine) {
		super(engine);
	}

	@Override
	public IdCardResult parseResults(List<PPOcrV6Result> results) {
		IdCardSide side = detectSide(results);
		IdCardResult r = new IdCardResult();
		r.setRawResults(new java.util.ArrayList<>(results));
		r.setSide(side);
		if (side == IdCardSide.FRONT) {
			r.setName(LabelMatcher.matchValueFromPrefix(results, "姓名"));
			r.setGender(parseGender(results));
			r.setNation(parseNation(results));
			r.setBirthDate(parseBirthDate(results));
			r.setAddress(parseAddress(results));
			r.setIdNumber(parseIdNumber(results));
		} else if (side == IdCardSide.BACK) {
			r.setIssuingAuthority(LabelMatcher.matchValueFromPrefix(results, "签发机关"));
			r.setValidFrom(null);
			r.setValidTo(null);
			String[] term = parseValidTerm(results);
			if (term != null) {
				r.setValidFrom(term[0]);
				r.setValidTo(term[1]);
			}
		}
		return r;
	}

	/**
	 * 版面判定：扫描特定标签的存在性。
	 *
	 * <p>先判断反面：反面字少、OCR 不易识别错误，优先级高于正面。
	 * 避免反面 OCR 残片（如"身"/"份"被匹配为"公民身份号码"残缺标签）导致误判为正面。
	 */
	private static IdCardSide detectSide(List<PPOcrV6Result> results) {
		// 先判断反面（反面字少，OCR 不易出错）
		boolean back = LabelMatcher.findLabelBox(results, "签发机关") != null
			|| LabelMatcher.findLabelBox(results, "有效期限") != null;
		if (back) {
			return IdCardSide.BACK;
		}
		boolean front = LabelMatcher.findLabelBox(results, "姓名") != null
			|| LabelMatcher.findLabelBox(results, "公民身份号码") != null;
		if (front) {
			return IdCardSide.FRONT;
		}
		log.warn("身份证解析：未能识别版面（无正面/反面特征标签）");
		return IdCardSide.UNKNOWN;
	}

	/**
	 * 性别提取：优先标签定位，兼容 "性别男民族汉" 双标签连写合并框。
	 */
	private static String parseGender(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "性别");
		if (labelValue != null) {
			return cutAtNextLabel(labelValue);
		}
		// 合并框（"性别男民族汉"）：从任意文本中按 "性别" 标签切出
		return LabelMatcher.matchSubstring(results, text -> cutAtNextLabel(afterLabel(text, "性别")));
	}

	/**
	 * 民族提取：优先标签定位，兼容 "性别男民族汉" 双标签连写合并框。
	 */
	private static String parseNation(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "民族");
		if (labelValue != null) {
			return labelValue;
		}
		// 合并框（"性别男民族汉"）：从任意文本中按 "民族" 标签切出
		return LabelMatcher.matchSubstring(results, text -> afterLabel(text, "民族"));
	}

	/**
	 * 取指定标签之后的文本，截断到下一个正面标签（合并框 "性别男民族汉" 切分用）。
	 *
	 * @param text  OCR 文本
	 * @param label 当前字段标签
	 * @return 标签之后到下一个标签之间的值；无标签或无值返回 null
	 */
	private static String afterLabel(String text, String label) {
		int idx = text.indexOf(label);
		if (idx < 0) {
			return null;
		}
		String rest = text.substring(idx + label.length());
		int end = rest.length();
		for (String next : FRONT_LABELS) {
			if (next.equals(label)) {
				continue;
			}
			int j = rest.indexOf(next);
			if (j >= 0 && j < end) {
				end = j;
			}
		}
		String value = rest.substring(0, end).trim();
		return value.isEmpty() ? null : value;
	}

	/**
	 * 合并框值截断：性别值后紧接 "民族汉" 时，只保留到下一个标签前。
	 */
	private static String cutAtNextLabel(String value) {
		if (value == null) {
			return null;
		}
		int end = value.length();
		for (String next : FRONT_LABELS) {
			if (next.equals("性别")) {
				continue;
			}
			int j = value.indexOf(next);
			if (j >= 0 && j < end) {
				end = j;
			}
		}
		String cut = value.substring(0, end).trim();
		return cut.isEmpty() ? null : cut;
	}

	/**
	 * 出生日期提取：按"出生"标签定位，可能跨多框（"1996 年 11 月 2 日"）。
	 *
	 * <p>取最靠左的 y 重叠框，与"出生"标签同行。
	 * 若标签定位结果不可用，回退正则（4 位年 + 1~2 位月 + 1~2 位日）。
	 */
	private static String parseBirthDate(List<PPOcrV6Result> results) {
		// 支持"出生1966年11月2日"合并框识别
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "出生");
		if (labelValue != null) {
			// 出生日期格式较自由（"1966 年 11 月 2 日"），先信任标签定位结果
			return labelValue;
		}
		return LabelMatcher.matchPattern(results, BIRTH_DATE_PATTERN, false);
	}

	/**
	 * 住址提取：按"住址"标签定位，可能跨多框（"安徽省宿州.../庄镇"）。
	 *
	 * <p>住址可能换行，所以需要拼接多个 y 重叠的右侧框。
	 */
	private static String parseAddress(List<PPOcrV6Result> results) {
		// 先用 findLabelBox 找独立"住址"标签；如果 OCR 把"住址"识别成"住址XXX"合并框，
		// 则返回的 labelBox 是合并框，需要从中剥出独立的"住址"标签框（构造虚拟框）。
		PPOcrV6Result labelBox = LabelMatcher.findLabelBox(results, "住址");
		if (labelBox == null) {
			log.warn("身份证解析：未找到标签 \"住址\"");
			return null;
		}

		// 检查是否是合并框（"住址"被识别成"住址XXX"）；若是，剥出"住址"文本部分的值（地址第一行）
		String labelText = labelBox.text();
		List<PPOcrV6Result> candidates = new java.util.ArrayList<>();
		String firstLineFromMerged = null;
		if (labelText.startsWith("住址") && labelText.length() > 2) {
			// 合并框：第一行地址已含在 labelBox 中，剥前缀得到
			firstLineFromMerged = labelText.substring(2);
			// 不再需要 y 重叠的右侧框（合并框里已含第一行），只找后续跨行框
		}

		int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);

		// 收集 y 范围与标签重叠的右侧框，按 y 升序拼接
		for (PPOcrV6Result r : results) {
			if (r == labelBox) {
				continue;
			}
			String text = r.text();
			// 跳过含身份证号标签/纯日期/纯英文标签的框
			if (text.contains("公民身份号码") || text.contains("身份证号")
				|| text.contains("签发机关") || text.contains("有效期限")
				|| text.contains("-")) {
				continue;
			}
			// 值框中心 x 必须在标签中心 x 右侧（兼容 OCR 边界框部分重叠）
			int x0 = LabelMatcher.minX(r);
			int rCenterX = (x0 + LabelMatcher.maxX(r)) / 2;
			if (rCenterX <= labelCenterX) {
				continue;
			}
			int rMinY = LabelMatcher.minY(r);
			int rMaxY = LabelMatcher.maxY(r);
			// y 范围必须与标签框有重叠（允许下方扩展 1 行住址：到 labelMaxY + 一行文本高度）
			int oneLine = (labelMaxY - labelMinY);
			if (rMaxY < labelMinY || rMinY > labelMaxY + oneLine) {
				continue;
			}
			candidates.add(r);
		}
		StringBuilder sb = new StringBuilder();
		if (firstLineFromMerged != null) {
			sb.append(firstLineFromMerged);
		}
		// 按 y 升序拼接后续跨行框
		candidates.sort(Comparator.comparingInt(LabelMatcher::minY));
		for (PPOcrV6Result r : candidates) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(r.text());
		}
		// 去掉内部空白（OCR 噪声 + 拼接引入的空格）
		String result = sb.toString().replaceAll("\\s+", "");
		return result.isEmpty() ? null : result;
	}

	/**
	 * 公民身份号码：标签定位优先，18 位正则 find() 兜底。
	 *
	 * <p>标签可能残缺（"公民身份号3625..."）或与号码合并成同一框
	 * （"公民身份号码3625..."），故正则兜底用 find() 从任意文本中提取 18 位连续号码。
	 */
	private static String parseIdNumber(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "公民身份号码");
		if (labelValue != null && ID_NUMBER_PATTERN.matcher(labelValue).matches()) {
			return labelValue;
		}
		String fallback = LabelMatcher.matchSubstring(results, text -> {
			Matcher m = ID_NUMBER_PATTERN.matcher(text);
			return m.find() ? m.group() : null;
		});
		if (fallback != null) {
			log.debug("身份证解析：身份证号正则兜底命中 \"{}\"", fallback);
			return fallback;
		}
		log.warn("身份证解析：未匹配到身份证号");
		return null;
	}

	/**
	 * 有效期限：按 "有效期限" 标签定位；解析 "YYYY.MM.DD[-YYYY.MM.DD]" 格式。
	 *
	 * @return [validFrom, validTo]，单段时两端相同；解析失败返回 null
	 */
	private static String[] parseValidTerm(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValue(results, "有效期限");
		if (labelValue == null) {
			labelValue = LabelMatcher.matchPattern(results, VALID_TERM_PATTERN, false);
		}
		if (labelValue == null) {
			log.warn("身份证解析：未匹配到有效期限");
			return null;
		}
		// "长期"：两端都填 "长期"
		if (labelValue.contains("长期")) {
			return new String[]{"长期", "长期"};
		}
		String[] parts = labelValue.split("-");
		if (parts.length == 1) {
			return new String[]{parts[0], parts[0]};
		}
		return new String[]{parts[0], parts[1]};
	}
}

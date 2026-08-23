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

import java.util.ArrayList;
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
 *       公民身份号码用 18/15 位正则兜底（应对 OCR 残缺 "公民身份号码" 标签）。</li>
 *   <li><b>反面字段</b>：签发机关 按 "签发机关" 标签定位；
 *       有效期限 从 "YYYY.MM.DD[-YYYY.MM.DD]" 文本中按 "." 分隔符切出起止。</li>
 *   <li><b>15 位兼容</b>：早期签发的 15 位身份证号（无校验位、第 7-12 位为 YYMMDD）
 *       一并支持；出生日期 OCR 残缺时从身份证号推算（15 位 YY 默认按 19YY 补全）。</li>
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
	private static final Pattern ID_NUMBER_18_PATTERN = Pattern.compile("[0-9]{17}[0-9X]");
	/**
	 * 公民身份号码：15 位（早期身份证号，无校验位）。
	 */
	private static final Pattern ID_NUMBER_15_PATTERN = Pattern.compile("[0-9]{15}");
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
		r.setRawResults(new ArrayList<>(results));
		r.setSide(side);
		if (side == IdCardSide.FRONT) {
			r.setName(LabelMatcher.matchValueFromPrefix(results, "姓名"));
			r.setGender(parseGender(results));
			r.setNation(parseNation(results));
			r.setIdNumber(parseIdNumber(results));
			r.setBirthDate(parseBirthDate(results, r.getIdNumber()));
			r.setAddress(parseAddress(results));
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
	 * 性别提取：优先尝试合并框切割（兼容 "性别男民族汉" 双标签连写合并框），未命中时走标准标签定位。
	 */
	private static String parseGender(List<PPOcrV6Result> results) {
		String mergedValue = LabelMatcher.matchSubstring(results, text -> cutAtNextLabel(afterLabel(text, "性别")));
		if (mergedValue != null) {
			return mergedValue;
		}
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "性别");
		return cutAtNextLabel(labelValue);
	}

	/**
	 * 民族提取：优先尝试合并框切割（兼容 "性别男民族汉" 双标签连写合并框），未命中时走标准标签定位。
	 */
	private static String parseNation(List<PPOcrV6Result> results) {
		String mergedValue = LabelMatcher.matchSubstring(results, text -> afterLabel(text, "民族"));
		if (mergedValue != null) {
			return mergedValue;
		}
		return LabelMatcher.matchValueFromPrefix(results, "民族");
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
	 * 若标签定位结果不可用，回退正则（4 位年 + 1~2 位月 + 1~2 位日）；
	 * 仍不可用时，从身份证号推算（15 位 YY 默认按 19YY 补全，18 位 YYYY 直接取）。
	 */
	private static String parseBirthDate(List<PPOcrV6Result> results, String idNumber) {
		// 支持"出生1966年11月2日"合并框识别
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "出生");
		if (labelValue != null) {
			// 出生日期格式较自由（"1966 年 11 月 2 日"），先信任标签定位结果
			return labelValue;
		}
		String pattern = LabelMatcher.matchPattern(results, BIRTH_DATE_PATTERN, false);
		if (pattern != null) {
			return pattern;
		}
		// 兜底：身份证号推算（兼容 15 位/18 位 + OCR "出生" 标签整体残缺场景）
		String fromId = birthDateFromIdNumber(idNumber);
		if (fromId != null) {
			log.debug("身份证解析：出生日期身份证号推算命中 \"{}\"", fromId);
		}
		return fromId;
	}

	/**
	 * 从身份证号推算出生日期（"yyyy 年 MM 月 dd 日" 格式）。
	 *
	 * <p>15 位身份证号为 YYMMDD，按 GB 11643-1999 早期签发规则默认按 19YY 补全。
	 * 18 位身份证号为 YYYYMMDD。
	 *
	 * @param idNumber 15/18 位身份证号
	 * @return 出生日期；长度不匹配返回 null
	 */
	private static String birthDateFromIdNumber(String idNumber) {
		if (idNumber == null) {
			return null;
		}
		if (idNumber.length() == 18) {
			return idNumber.substring(6, 10) + "年"
				+ idNumber.substring(10, 12) + "月"
				+ idNumber.substring(12, 14) + "日";
		}
		if (idNumber.length() == 15) {
			// 15 位 YY 默认按 19YY 补全（早期 15 位身份证号均为 19XX 年签发）
			return "19" + idNumber.substring(6, 8) + "年"
				+ idNumber.substring(8, 10) + "月"
				+ idNumber.substring(10, 12) + "日";
		}
		return null;
	}

	/**
	 * 住址提取：按"住址"标签定位，支持合并框及跨多框/跨行（如"四川省金堂县平桥乡清堰" + "1组"）。
	 *
	 * <p>住址可能换行，需要拼接多个几何重叠或延伸的右侧/下方框。
	 *
	 * <p>核心算法逻辑：
	 * 1. 先用 {@link LabelMatcher#findLabelBox} 找"住址"标签；如果 OCR 把"住址"识别成"住址XXX"合并框，
	 *    则返回的 labelBox 是合并框，从中剥出独立的地址第一行（{@code firstLineFromMerged}）。
	 * 2. 动态确定下界止点：寻找"公民身份号码"标签或身份证号框的 Y 轴上边缘作为地址区域物理止点，防止吸收底部无关噪声。
	 * 3. X 轴放宽判定：废除对绝对 labelCenterX 的约束，使用 {@code rMaxX >= labelMinX - 10} 判定，确保左下角短续行（如"1组"）不被误杀。
	 * 4. 二维几何拓扑排序：同行按 X 升序，跨行按 Y 升序，保证多框拼接顺序准确无误。
	 */
	private static String parseAddress(List<PPOcrV6Result> results) {
		// 先用 findLabelBox 找独立"住址"标签；如果 OCR 把"住址"识别成"住址XXX"合并框，
		// 则返回的 labelBox 是合并框，需要从中剥出独立的"住址"标签框（构造虚拟框）。
		PPOcrV6Result labelBox = LabelMatcher.findLabelBox(results, "住址");
		if (labelBox == null) {
			log.warn("身份证解析：未找到标签 \"住址\"");
			return null;
		}

		// 1. 动态确定住址区域的垂直下界止点 (Bottom Limit)
		// 寻找"公民身份号码"或身份证号框作为住址区域的下界止点
		int bottomLimitY = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.contains("公民身份号码") || text.contains("身份证号")
				|| ID_NUMBER_18_PATTERN.matcher(text).find()
				|| ID_NUMBER_15_PATTERN.matcher(text).find()) {
				int minY = LabelMatcher.minY(r);
				if (minY < bottomLimitY) {
					bottomLimitY = minY;
				}
			}
		}

		// 2. 检查是否是合并框（"住址"被识别成"住址XXX"）；若是，剥出"住址"文本部分的值（地址第一行）
		String labelText = labelBox.text();
		List<PPOcrV6Result> candidates = new ArrayList<>();
		String firstLineFromMerged = null;
		if (labelText.startsWith("住址") && labelText.length() > 2) {
			// 合并框：第一行地址已含在 labelBox 中，剥前缀得到
			firstLineFromMerged = labelText.substring(2);
			// 不再需要 y 重叠的右侧框（合并框里已含第一行），只找后续跨行框
		}

		int labelMinX = LabelMatcher.minX(labelBox);
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		int oneLineHeight = Math.max(labelMaxY - labelMinY, 15);

		// 3. 收集 y 范围与标签重叠或向下方延伸的候选框
		for (PPOcrV6Result r : results) {
			if (r == labelBox) {
				continue;
			}
			String text = r.text();
			if (text.isEmpty()) {
				continue;
			}

			// 跳过含身份证号/姓名/性别/民族/出生等其他已知标签及纯日期/纯英文标签的干扰框
			if (containsAnyLabelOrId(text)) {
				continue;
			}

			int rMaxX = LabelMatcher.maxX(r);
			// 值框右边缘必须在"住址"标签左边缘之后（允许 10px 倾斜/容差，兼容左下角短文本续行）
			if (rMaxX < labelMinX - 10) {
				continue;
			}

			int rMinY = LabelMatcher.minY(r);
			int rMaxY = LabelMatcher.maxY(r);

			// y 范围控制：
			// a. 框下边缘不能高于"住址"标签上边缘
			if (rMaxY < labelMinY - 5) {
				continue;
			}
			// b. 框上边缘必须高于身份证号码下界止点
			if (rMinY >= bottomLimitY - 2) {
				continue;
			}
			// c. 若未识别到号码框下界，限制最多在住址标签下方延伸 4 行
			if (bottomLimitY == Integer.MAX_VALUE && rMinY > labelMaxY + 4 * oneLineHeight) {
				continue;
			}

			candidates.add(r);
		}

		// 4. 二维几何拓扑排序：优先按 y 升序（从上到下），同行（y 差值在 10px 内）按 x 升序（从左到右）
		candidates.sort((r1, r2) -> {
			int yDiff = LabelMatcher.minY(r1) - LabelMatcher.minY(r2);
			if (Math.abs(yDiff) <= 10) {
				return Integer.compare(LabelMatcher.minX(r1), LabelMatcher.minX(r2));
			}
			return Integer.compare(LabelMatcher.minY(r1), LabelMatcher.minY(r2));
		});

		// 5. 组装与清洗
		StringBuilder sb = new StringBuilder();
		if (firstLineFromMerged != null) {
			sb.append(firstLineFromMerged);
		}
		// 按排序顺序拼接后续跨行框
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
	 * 检查文本是否包含其他已知正面/反面字段关键字或身份证号
	 */
	private static boolean containsAnyLabelOrId(String text) {
		if (text.contains("公民身份号码") || text.contains("身份证号")
			|| text.contains("姓名") || text.contains("性别")
			|| text.contains("民族") || text.contains("出生")
			|| text.contains("签发机关") || text.contains("有效期限")) {
			return true;
		}
		return ID_NUMBER_18_PATTERN.matcher(text).find()
			|| ID_NUMBER_15_PATTERN.matcher(text).find();
	}

	/**
	 * 公民身份号码：标签定位优先，正则 find() 兜底（18 位优先，15 位兜底）。
	 *
	 * <p>标签可能残缺（"公民身份号3625..."）或与号码合并成同一框
	 * （"公民身份号码3625..."），故正则兜底用 find() 从任意文本中提取。
	 * 18 位号码的子序列（前 15 位数字）可能误匹配 15 位正则，故先尝试 18 位。
	 */
	private static String parseIdNumber(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValueFromPrefix(results, "公民身份号码");
		if (labelValue != null && isValidIdNumber(labelValue)) {
			return labelValue;
		}
		// 兜底：18 位优先（避免误把 18 位的前 15 位识别成 15 位号码）
		String fallback = LabelMatcher.matchSubstring(results, text -> {
			Matcher m = ID_NUMBER_18_PATTERN.matcher(text);
			if (m.find()) {
				return m.group();
			}
			m = ID_NUMBER_15_PATTERN.matcher(text);
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
	 * 校验文本是否为 15 位或 18 位身份证号。
	 *
	 * @param text 待校验文本
	 * @return true 表示文本完整匹配 15/18 位身份证号
	 */
	private static boolean isValidIdNumber(String text) {
		return text != null
			&& (ID_NUMBER_18_PATTERN.matcher(text).matches()
				|| ID_NUMBER_15_PATTERN.matcher(text).matches());
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

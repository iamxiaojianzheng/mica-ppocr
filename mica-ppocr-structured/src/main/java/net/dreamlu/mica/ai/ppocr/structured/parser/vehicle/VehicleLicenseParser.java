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

package net.dreamlu.mica.ai.ppocr.structured.parser.vehicle;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher.LabeledMatch;
import net.dreamlu.mica.ai.ppocr.utils.CollUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行驶证 OCR 结构化解析器。
 *
 * <p>采用"标签定位 + 位置匹配"策略：对每个字段标签（号牌号码/车辆类型/所有人/车辆识别代号/发证日期），
 * 找到标签框后，在 x 起点位于标签右边缘右侧（容忍边界 1px 相接）、y 范围与标签框重叠的
 * 候选值框中，取最靠左（x 最小）的文本作为字段值。
 *
 * <p>输出结果会填充 {@code VehicleLicenseResult#getRawResults()}（完整 OCR 结果）
 * 与 {@code VehicleLicenseResult#getFieldBoxes()}（字段名 → box 坐标列表），
 * 方便调用方在页面上复原并高亮对应字段。
 */
@Slf4j
public class VehicleLicenseParser extends BaseStructuredParser<VehicleLicenseResult> {

	// ==================================================================
	// 正则常量
	// ==================================================================

	/**
	 * 车牌正则：兼容普通车牌（省简称+字母+5~6位数字字母）与挂车车牌（省简称+字母+4~5位数字字母+挂）。
	 * 挂车车牌如 "津A0000挂"、"鲁P0000挂"、"京A0000挂"。
	 */
	private static final Pattern PLATE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{4,5}挂?");
	/**
	 * 车辆识别代号（VIN）：17 位字母数字。
	 */
	private static final Pattern VIN_PATTERN = Pattern.compile("[A-Z0-9]{17}");
	/**
	 * 日期：yyyy-MM-dd。
	 */
	private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	/**
	 * 纯英文/空白（用于车辆类型下沿的兜底判断）。
	 */
	private static final Pattern ENGLISH_TEXT_PATTERN = Pattern.compile("[A-Za-z\\s]+");
	/**
	 * 纯英文/空白/点号（布局兜底时排除的噪声文本）。
	 */
	private static final Pattern ENGLISH_TEXT_DOT_PATTERN = Pattern.compile("[A-Za-z\\s.]+");
	/**
	 * 含中文字符（公司名/人名特征）。
	 */
	private static final Pattern CHINESE_TEXT_PATTERN = Pattern.compile(".*[\\u4e00-\\u9fa5].*");
	/**
	 * 车辆类型值（如 "重型半挂牵引车"、"重型集装箱半挂车"）。
	 */
	private static final Pattern VEHICLE_TYPE_VALUE_PATTERN =
		Pattern.compile("(重型|小型|中型|大型|微型|普通).*(牵引车|客车|轿车|货车|挂车|面包车|专用车)");
	/**
	 * 地址特征关键字（省市区县路街道号镇村）。
	 */
	private static final Pattern ADDRESS_KEYWORD_PATTERN = Pattern.compile(".*[省市区县路街道号镇村].*");

	// ==================================================================
	// 字段标签常量
	// ==================================================================

	private static final String LABEL_PLATE_NO = "号牌号码";
	private static final String LABEL_OWNER = "所有人";
	/** OCR 常把"所有人"识别成残缺"所人"（缺"有"） */
	private static final String LABEL_OWNER_PARTIAL = "所人";
	private static final String LABEL_OWNER_EN = "Owner";
	private static final String LABEL_VEHICLE_TYPE = "车辆类型";
	private static final String LABEL_VIN = "车辆识别代号";
	private static final String LABEL_ISSUE_DATE = "发证日期";

	// ==================================================================
	// 布局兜底常量
	// ==================================================================

	/** 车辆类型标签候选（中文/英文） */
	private static final String[] VEHICLE_TYPE_LABELS = {LABEL_VEHICLE_TYPE, "VehicleType"};
	/** 住址标签候选（含英文别名） */
	private static final String[] ADDRESS_LABELS = {"住址", "住", "址", "Address", "Adder"};
	/** 残缺"所有人"前缀（OCR 把"所有人"误识成"人"/"有人"/"所人"并与值合并） */
	private static final String[] OWNER_PARTIAL_PREFIXES = {"有人", "所人", "人"};
	/** 保护"中国人民…"等合法公司名：剥"人"后以"国"开头则拒绝 */
	private static final String RENMIN_GUARD = "国";
	/** 地址前缀（"址山东省…"） */
	private static final String ADDRESS_PREFIX = "址";
	/** 公司特征关键字：含则视为公司名而非地址 */
	private static final Set<String> COMPANY_KEYWORDS = CollUtil.newHashSet(
		"有限公司", "运输", "物流", "租赁", "商贸", "个体"
	);
	/** 所有人版面布局兜底时需排除的已知标签 / 标题 / 英文标签噪声 */
	private static final Set<String> OWNER_NOISE_LABELS = CollUtil.newHashSet(
		LABEL_PLATE_NO, LABEL_VEHICLE_TYPE, LABEL_VIN, "发动机号码", "注册日期", LABEL_ISSUE_DATE,
		"使用性质", "品牌型号", "检验有效期", "强制报废期止", "档案编号", "核定载人数",
		"总质量", "整备质量", "核定载质量", "外廓尺寸", "准牵引总质量", "检验记录",
		"中华人民共和国机动车行驶证",
		"PlateNo", "VehicleType", LABEL_OWNER_EN, "Model", "Address", "UseCharacter",
		"VIN", "EngineNo", "RegisterDate", "IssueDate"
	);

	/**
	 * 构造行驶证解析器，绑定推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎；可为 null（仅在仅调用 {@link #parseResults(List)} 时）
	 */
	public VehicleLicenseParser(PPOcrV6Engine engine) {
		super(engine);
	}

	@Override
	public VehicleLicenseResult parseResults(List<PPOcrV6Result> results) {
		VehicleLicenseResult result = new VehicleLicenseResult();
		// 塞原始 OCR 结果，供调用方做可视化
		result.setRawResults(new ArrayList<>(results));

		// 1. 车牌
		LabeledMatch plateMatch = parsePlateNo(results);
		result.setPlateNo(plateMatch.value());
		LabelMatcher.applyFieldBox(result, "plateNo", plateMatch);

		// 2. 所有人
		LabeledMatch ownerMatch = parseOwner(results);
		result.setOwner(ownerMatch.value());
		LabelMatcher.applyFieldBox(result, "owner", ownerMatch);

		// 3. 车辆类型
		LabeledMatch vehicleTypeMatch = parseVehicleType(results);
		result.setVehicleType(vehicleTypeMatch.value());
		LabelMatcher.applyFieldBox(result, "vehicleType", vehicleTypeMatch);

		// 4. VIN
		LabeledMatch vinMatch = parseVin(results);
		result.setVin(vinMatch.value());
		LabelMatcher.applyFieldBox(result, "vin", vinMatch);

		// 5. 发证日期
		LabeledMatch dateMatch = parseIssueDate(results);
		result.setIssueDate(dateMatch.value());
		LabelMatcher.applyFieldBox(result, "issueDate", dateMatch);

		return result;
	}

	// ==================================================================
	// 各字段解析
	// ==================================================================

	/**
	 * 解析车牌：合并框剥前缀 → 标签定位+正则兜底 → 子串搜索兜底（三个分支都能拿到 box）。
	 */
	private static LabeledMatch parsePlateNo(List<PPOcrV6Result> results) {
		LabeledMatch match = LabelMatcher.matchValueFromPrefixWithBox(results, LABEL_PLATE_NO);
		if (!match.hasValue() || !PLATE_PATTERN.matcher(match.value()).matches()) {
			match = LabelMatcher.labelOrFallbackWithBox(
				LabelMatcher.matchValueWithBox(results, LABEL_PLATE_NO),
				results, PLATE_PATTERN, "车牌", false);
		}
		if (!match.hasValue()) {
			match = matchSubstringWithBox(results, PLATE_PATTERN);
			if (match.hasValue()) {
				log.debug("行驶证解析：车牌 子串搜索兜底命中 \"{}\"", match.value());
			}
		}
		return match;
	}

	/**
	 * 解析所有人：合并框（"所有人xxx"）→ 中文标签 → 残缺标签"所人" → 残缺前缀"人xxx"合并框 → 英文别名 → 版面布局兜底。
	 *
	 * <p>OCR 常把"所有人"+"姓名"识别成单框"所有人郑昆"——先按合并框剥前缀；
	 * 也可能识别成残缺"所人"（缺"有"）或"人"+"公司名"合并框。
	 */
	private static LabeledMatch parseOwner(List<PPOcrV6Result> results) {
		LabeledMatch match = LabelMatcher.matchValueFromPrefixWithBox(results, LABEL_OWNER);
		if (!match.hasValue()) {
			match = LabelMatcher.matchValueWithBox(results, LABEL_OWNER);
		}
		if (!match.hasValue()) {
			match = LabelMatcher.matchValueWithBox(results, LABEL_OWNER_PARTIAL);
			if (match.hasValue()) {
				log.debug("行驶证解析：所有人 按残缺标签\"所人\"命中 \"{}\"", match.value());
			}
		}
		if (!match.hasValue()) {
			// 残缺前缀"人xxx"合并框（如 "人莘县顺发物流有限公司"）优先于英文标签，
			// 因为旋转图中 Owner 标签右侧可能命中噪声片段（如 "国"）。
			match = matchOwnerByPartialPrefix(results);
			if (match.hasValue()) {
				log.debug("行驶证解析：所有人 按残缺前缀\"人\"剥值命中 \"{}\"", match.value());
			}
		}
		if (!match.hasValue()) {
			match = LabelMatcher.matchValueWithBox(results, LABEL_OWNER_EN);
			if (match.hasValue()) {
				log.debug("行驶证解析：所有人 按英文标签 Owner fallback 命中 \"{}\"", match.value());
			} else {
				match = matchOwnerByLayoutFallback(results);
				if (match.hasValue()) {
					log.debug("行驶证解析：所有人 按版面布局 fallback 命中 \"{}\"", match.value());
				}
			}
		}
		// Owner 标签命中合并框（"人莘县顺发物流有限公司"）时，值仍带残缺"人"前缀，统一剥除
		return normalizeOwnerMatch(match);
	}

	/**
	 * 解析车辆类型：合并框剥前缀（"车辆类型重型集装箱半挂车"）+ 标签定位 + 正则兜底 + 子串搜索兜底。
	 * OCR 常把"车辆类型"标签识别残缺（如"车辆5型"）或完全缺失，此时按车辆类型值正则兜底。
	 */
	private static LabeledMatch parseVehicleType(List<PPOcrV6Result> results) {
		LabeledMatch match = LabelMatcher.matchValueFromPrefixWithBox(results, LABEL_VEHICLE_TYPE);
		if (!match.hasValue()) {
			match = LabelMatcher.labelOrFallbackWithBox(
				match, results, VEHICLE_TYPE_VALUE_PATTERN, "车辆类型", false);
		}
		if (!match.hasValue()) {
			match = matchSubstringWithBox(results, VEHICLE_TYPE_VALUE_PATTERN);
			if (match.hasValue()) {
				log.debug("行驶证解析：车辆类型 子串搜索兜底命中 \"{}\"", match.value());
			}
		}
		return match;
	}

	/**
	 * 解析 VIN：标签定位 + 正则兜底 + 子串搜索兜底（含点号噪声清理）。
	 */
	private static LabeledMatch parseVin(List<PPOcrV6Result> results) {
		LabeledMatch match = LabelMatcher.labelOrFallbackWithBox(
			LabelMatcher.matchValueWithBox(results, LABEL_VIN),
			results, VIN_PATTERN, "VIN", false);
		if (!match.hasValue()) {
			match = matchSubstringWithBox(results, VIN_PATTERN);
			if (match.hasValue()) {
				log.debug("行驶证解析：VIN 子串搜索兜底命中 \"{}\"", match.value());
			}
		}
		if (!match.hasValue()) {
			// 点号噪声兜底：OCR 把 VIN 识别成 "LA9JM4C08T0HL10.1.3"（中间插了点号），
			// 清理非字母数字字符后按 17 位重新匹配。
			match = LabelMatcher.matchSubstringWithBox(results, text -> {
				Matcher m = VIN_PATTERN.matcher(text.replaceAll("[^A-Z0-9]", ""));
				return m.find() ? m.group() : null;
			});
			if (match.hasValue()) {
				log.debug("行驶证解析：VIN 点号噪声清理兜底命中 \"{}\"", match.value());
			}
		}
		return match;
	}

	/**
	 * 解析发证日期：标签定位 + 正则兜底 + 子串搜索兜底。
	 */
	private static LabeledMatch parseIssueDate(List<PPOcrV6Result> results) {
		LabeledMatch match = LabelMatcher.labelOrFallbackWithBox(
			LabelMatcher.matchValueWithBox(results, LABEL_ISSUE_DATE),
			results, DATE_PATTERN, "发证日期", true);
		if (!match.hasValue()) {
			match = matchSubstringWithBox(results, DATE_PATTERN);
			if (match.hasValue()) {
				log.debug("行驶证解析：发证日期 子串搜索兜底命中 \"{}\"", match.value());
			}
		}
		return match;
	}

	// ==================================================================
	// 兜底辅助
	// ==================================================================

	/**
	 * 在所有 OCR 文本上用正则 find() 提取首个匹配（应对标签缺失或值嵌在长合并框中的场景）。
	 *
	 * @param results OCR 识别结果列表
	 * @param pattern 值正则
	 * @return 匹配值 + 值框；无匹配时返回仅含 null value 的 LabeledMatch
	 */
	private static LabeledMatch matchSubstringWithBox(List<PPOcrV6Result> results, Pattern pattern) {
		return LabelMatcher.matchSubstringWithBox(results, text -> {
			Matcher m = pattern.matcher(text);
			return m.find() ? m.group() : null;
		});
	}

	/**
	 * OCR 把"所有人"识别成残缺前缀（"人"/"有人"/"所人"）并与公司/人名合并成单框
	 * （如 "人莘县顺发物流有限公司"、"有人新乐市云翔运输有限公司"、"所人上海润升物流有限公司"）
	 * 时，从合并框剥掉残缺前缀。
	 */
	private static LabeledMatch matchOwnerByPartialPrefix(List<PPOcrV6Result> results) {
		for (PPOcrV6Result box : results) {
			String stripped = stripOwnerRenPrefix(box.text());
			if (stripped != null) {
				log.debug("行驶证解析：所有人 从残缺合并框 \"{}\" 剥出 \"{}\"", box.text(), stripped);
				return LabeledMatch.of(stripped, box);
			}
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 剥掉所有人值开头的残缺"所有人"前缀（OCR 把"所有人"误识成"人"/"有人"/"所人"并与值合并）。
	 * 保护"中国人民财产保险…"等合法以"人"开头的公司名（剥后以"国"开头则拒绝）。
	 *
	 * @param text 所有人文本
	 * @return 剥前缀后的值；不满足剥除条件时返回 null
	 */
	private static String stripOwnerRenPrefix(String text) {
		if (text == null) {
			return null;
		}
		for (String prefix : OWNER_PARTIAL_PREFIXES) {
			if (text.startsWith(prefix) && text.length() > prefix.length()) {
				String stripped = text.substring(prefix.length());
				// 剥掉前缀后应含中文字符（公司名/人名），排除纯英文或明显噪声；
				// 同时保护"中国人民…"这类合法公司名（剥"人"后以"国"开头则拒绝）。
				if (!stripped.trim().isEmpty()
					&& CHINESE_TEXT_PATTERN.matcher(stripped).matches()
					&& !stripped.startsWith(RENMIN_GUARD)) {
					return stripped;
				}
			}
		}
		return null;
	}

	/**
	 * 规范化所有人匹配结果：值带残缺"人"前缀（如 "人莘县顺发物流有限公司"）时统一剥除，
	 * 保留原值框。
	 *
	 * @param match 待处理的所有人匹配结果
	 * @return 剥前缀后的匹配结果；不满足剥除条件时原样返回
	 */
	private static LabeledMatch normalizeOwnerMatch(LabeledMatch match) {
		String value = match.value();
		String stripped = stripOwnerRenPrefix(value);
		if (stripped == null || stripped.equals(value)) {
			return match;
		}
		log.debug("行驶证解析：所有人 剥除残缺前缀\"人\" \"{}\" -> \"{}\"", value, stripped);
		return LabeledMatch.of(stripped, match.matches());
	}

	/**
	 * 判断文本是否为已知标签 / 标题 / 车辆类型值等噪声（布局兜底时排除）。
	 */
	private static boolean isOwnerNoise(String text) {
		return OWNER_NOISE_LABELS.contains(text)
			|| OWNER_NOISE_LABELS.stream().anyMatch(text::startsWith)
			|| VEHICLE_TYPE_VALUE_PATTERN.matcher(text).matches();
	}

	/**
	 * 判断文本是否为地址（布局兜底时排除）。
	 */
	private static boolean isLikelyAddress(String text) {
		// 含公司特征的不视为地址（如 "聊城市侨润物流有限公司"）
		return text.startsWith(ADDRESS_PREFIX)
			|| (ADDRESS_KEYWORD_PATTERN.matcher(text).matches() && !containsCompanyKeyword(text));
	}

	/**
	 * 判断文本是否含公司特征关键字。
	 */
	private static boolean containsCompanyKeyword(String text) {
		return COMPANY_KEYWORDS.stream().anyMatch(text::contains);
	}

	/**
	 * 定位车辆类型下沿（y 最大值），作为所有人布局兜底的上边界。
	 *
	 * @return 车辆类型下沿；标签缺失且无"车"字文本时返回 {@link Integer#MIN_VALUE}
	 */
	private static int findVehicleTypeBottom(List<PPOcrV6Result> results) {
		int bottom = Integer.MIN_VALUE;
		for (String label : VEHICLE_TYPE_LABELS) {
			PPOcrV6Result box = LabelMatcher.findLabelBox(results, label);
			if (box != null) {
				bottom = Math.max(bottom, LabelMatcher.maxY(box));
			}
		}
		if (bottom != Integer.MIN_VALUE) return bottom;
		// 标签缺失时，用含"车"的文本兜底
		for (PPOcrV6Result box : results) {
			String text = box.text();
			if (!ENGLISH_TEXT_PATTERN.matcher(text).matches()
				&& (text.contains("轿车") || text.contains("客车") || text.contains("货车") || text.contains("车"))) {
				bottom = Math.max(bottom, LabelMatcher.maxY(box));
			}
		}
		return bottom;
	}

	/**
	 * 定位住址上沿（y 最小值），作为所有人布局兜底的下边界。
	 *
	 * @return 住址上沿；标签缺失且无地址关键字文本时返回 {@link Integer#MAX_VALUE}
	 */
	private static int findAddressTop(List<PPOcrV6Result> results) {
		int top = Integer.MAX_VALUE;
		for (String label : ADDRESS_LABELS) {
			PPOcrV6Result box = LabelMatcher.findLabelBox(results, label);
			if (box != null) {
				top = Math.min(top, LabelMatcher.minY(box));
			}
		}
		if (top != Integer.MAX_VALUE) return top;
		// 标签缺失时，用含地址关键字的文本兜底
		for (PPOcrV6Result box : results) {
			if (ADDRESS_KEYWORD_PATTERN.matcher(box.text()).matches()) {
				top = Math.min(top, LabelMatcher.minY(box));
			}
		}
		return top;
	}

	/**
	 * 所有人版面布局兜底：在"车辆类型下沿"与"住址上沿"之间的 y 带内，
	 * 取最宽的非噪声文本作为所有人（无法精准定位 box，所以 fieldBoxes 不填）。
	 */
	private static LabeledMatch matchOwnerByLayoutFallback(List<PPOcrV6Result> results) {
		int vehicleTypeBottom = findVehicleTypeBottom(results);
		if (vehicleTypeBottom == Integer.MIN_VALUE) {
			return LabeledMatch.textOnly(null);
		}
		int addressTop = findAddressTop(results);
		if (addressTop == Integer.MAX_VALUE || addressTop <= vehicleTypeBottom) {
			return LabeledMatch.textOnly(null);
		}

		String best = null;
		int bestWidth = -1;
		for (PPOcrV6Result box : results) {
			if (!isOwnerCandidate(box, vehicleTypeBottom, addressTop)) continue;
			int width = LabelMatcher.maxX(box) - LabelMatcher.minX(box);
			if (width > bestWidth) {
				bestWidth = width;
				best = box.text();
			}
		}
		return LabeledMatch.textOnly(best);
	}

	/**
	 * 判断 OCR 框是否为所有人布局兜底的候选：
	 * 非空、非纯英文噪声、非已知标签/车辆类型值、非地址，且位于"车辆类型下沿"与"住址上沿"之间。
	 *
	 * @param box              待判断的 OCR 框
	 * @param vehicleTypeBottom 车辆类型下沿（y 下限）
	 * @param addressTop        住址上沿（y 上限）
	 * @return true 表示可作为所有人候选
	 */
	private static boolean isOwnerCandidate(PPOcrV6Result box, int vehicleTypeBottom, int addressTop) {
		String text = box.text();
		return !text.isEmpty()
			&& !ENGLISH_TEXT_DOT_PATTERN.matcher(text).matches()
			&& !isOwnerNoise(text)
			&& !isLikelyAddress(text)
			&& LabelMatcher.maxY(box) >= vehicleTypeBottom
			&& LabelMatcher.minY(box) <= addressTop;
	}
}

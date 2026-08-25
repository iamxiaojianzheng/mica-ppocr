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

import java.util.ArrayList;
import java.util.List;
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

	private static final Pattern PLATE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}");
	private static final Pattern VIN_PATTERN = Pattern.compile("[A-Z0-9]{17}");
	private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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
		return doParse(results);
	}

	private VehicleLicenseResult doParse(List<PPOcrV6Result> results) {
		VehicleLicenseResult license = new VehicleLicenseResult();
		// 塞原始 OCR 结果，供调用方做可视化
		license.setRawResults(new ArrayList<>(results));

		// 1. 车牌：标签定位 + 正则兜底（两个分支都能拿到 box）
		LabeledMatch plateMatch = LabelMatcher.labelOrFallbackWithBox(
			LabelMatcher.matchValueWithBox(results, "号牌号码"),
			results, PLATE_PATTERN, "车牌", false);
		license.setPlateNo(plateMatch.value());
		LabelMatcher.applyFieldBox(license, "plateNo", plateMatch);

		// 2. 所有人：合并框（"所有人xxx"）→ 中文标签 → 英文别名 → 版面布局兜底
		//    OCR 常把"所有人"+"姓名"识别成单框"所有人郑昆"——先按合并框剥前缀
		LabeledMatch ownerMatch = LabelMatcher.matchValueFromPrefixWithBox(results, "所有人");
		if (!ownerMatch.hasValue()) {
			ownerMatch = LabelMatcher.matchValueWithBox(results, "所有人");
		}
		if (!ownerMatch.hasValue()) {
			ownerMatch = LabelMatcher.matchValueWithBox(results, "Owner");
			if (ownerMatch.hasValue()) {
				log.debug("行驶证解析：所有人 按英文标签 Owner fallback 命中 \"{}\"", ownerMatch.value());
			} else {
				// 布局兜底单独处理
				String ownerText = matchOwnerByLayoutFallback(results);
				if (ownerText != null) {
					log.debug("行驶证解析：所有人 按版面布局 fallback 命中 \"{}\"", ownerText);
					ownerMatch = LabeledMatch.textOnly(ownerText);
				}
			}
		}
		license.setOwner(ownerMatch.value());
		LabelMatcher.applyFieldBox(license, "owner", ownerMatch);

		// 3. 车辆类型
		LabeledMatch vtMatch = LabelMatcher.matchValueWithBox(results, "车辆类型");
		license.setVehicleType(vtMatch.value());
		LabelMatcher.applyFieldBox(license, "vehicleType", vtMatch);

		// 4. VIN：标签定位 + 正则兜底 + 子串搜索兜底
		LabeledMatch vinMatch = LabelMatcher.labelOrFallbackWithBox(
			LabelMatcher.matchValueWithBox(results, "车辆识别代号"),
			results, VIN_PATTERN, "VIN", false);
		if (!vinMatch.hasValue()) {
			vinMatch = matchVINFallbackWithBox(results);
			if (vinMatch.hasValue()) {
				log.debug("行驶证解析：VIN 子串搜索兜底命中 \"{}\"", vinMatch.value());
			}
		}
		license.setVin(vinMatch.value());
		LabelMatcher.applyFieldBox(license, "vin", vinMatch);

		// 5. 发证日期：标签定位 + 正则兜底 + 子串搜索兜底
		LabeledMatch dateMatch = LabelMatcher.labelOrFallbackWithBox(
			LabelMatcher.matchValueWithBox(results, "发证日期"),
			results, DATE_PATTERN, "发证日期", true);
		if (!dateMatch.hasValue()) {
			dateMatch = matchDateFallbackWithBox(results);
			if (dateMatch.hasValue()) {
				log.debug("行驶证解析：发证日期 子串搜索兜底命中 \"{}\"", dateMatch.value());
			}
		}
		license.setIssueDate(dateMatch.value());
		LabelMatcher.applyFieldBox(license, "issueDate", dateMatch);

		return license;
	}

	private static LabeledMatch matchVINFallbackWithBox(List<PPOcrV6Result> results) {
		return LabelMatcher.matchSubstringWithBox(results, text -> {
			Matcher m = VIN_PATTERN.matcher(text);
			return m.find() ? m.group() : null;
		});
	}

	private static LabeledMatch matchDateFallbackWithBox(List<PPOcrV6Result> results) {
		return LabelMatcher.matchSubstringWithBox(results, text -> {
			Matcher m = DATE_PATTERN.matcher(text);
			return m.find() ? m.group() : null;
		});
	}

	// 所有人版面布局兜底：返回纯文本（无法精准定位 box，所以 fieldBoxes 不填）
	private static String matchOwnerByLayoutFallback(List<PPOcrV6Result> results) {
		int vehicleTypeBottom = Integer.MIN_VALUE;
		String[] vtCandidates = {"车辆类型", "VehicleType"};
		for (String lbl : vtCandidates) {
			PPOcrV6Result b = LabelMatcher.findLabelBox(results, lbl);
			if (b != null) {
				vehicleTypeBottom = Math.max(vehicleTypeBottom, LabelMatcher.maxY(b));
			}
		}
		if (vehicleTypeBottom == Integer.MIN_VALUE) {
			for (PPOcrV6Result r : results) {
				String t = r.text();
				if (!t.matches("[A-Za-z\\s]+") && (t.contains("轿车") || t.contains("客车") || t.contains("货车") || t.contains("车"))) {
					vehicleTypeBottom = Math.max(vehicleTypeBottom, LabelMatcher.maxY(r));
				}
			}
		}
		if (vehicleTypeBottom == Integer.MIN_VALUE) return null;

		int addressTop = Integer.MAX_VALUE;
		String[] addrCandidates = {"住址", "住", "址", "Address", "Adder"};
		for (String lbl : addrCandidates) {
			PPOcrV6Result b = LabelMatcher.findLabelBox(results, lbl);
			if (b != null) {
				addressTop = Math.min(addressTop, LabelMatcher.minY(b));
			}
		}
		if (addressTop == Integer.MAX_VALUE) {
			for (PPOcrV6Result r : results) {
				String t = r.text();
				if (t.matches(".*[省市区县路街道号镇村].*")) {
					addressTop = Math.min(addressTop, LabelMatcher.minY(r));
				}
			}
		}
		if (addressTop == Integer.MAX_VALUE || addressTop <= vehicleTypeBottom) return null;

		String best = null;
		int bestWidth = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) continue;
			if (text.matches("[A-Za-z\\s.]+")) continue;
			if ("所有人".contains(text) || "Owner".contains(text) || "owner".contains(text)) continue;
			if (LabelMatcher.maxY(r) < vehicleTypeBottom || LabelMatcher.minY(r) > addressTop) continue;
			int width = LabelMatcher.maxX(r) - LabelMatcher.minX(r);
			if (width > bestWidth) {
				bestWidth = width;
				best = text;
			}
		}
		return best;
	}
}

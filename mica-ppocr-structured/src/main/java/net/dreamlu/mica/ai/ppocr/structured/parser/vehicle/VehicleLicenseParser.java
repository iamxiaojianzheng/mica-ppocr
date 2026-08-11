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
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;

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
 * <p>找不到标签或值框时字段置 null，不中断。位置匹配天然能区分同值的
 * "注册日期/发证日期"（两个日期文本相同但位于不同标签右侧）。
 *
 * <p>车牌/VIN/发证日期在标签定位失败时按内容特征正则兜底；车辆类型为自由中文文本
 * 无固定格式，仅标签定位，不做正则兜底。
 *
 * <p>本类同时实现 {@link BaseStructuredParser}（实例方法，用于依赖注入场景）
 * 与静态 {@link #parse(List)} 入口（用于工具类风格调用）。
 */
@Slf4j
public class VehicleLicenseParser implements BaseStructuredParser<VehicleLicenseResult> {

	/**
	 * 单例实例，便于作为 {@code BaseStructuredParser<VehicleLicenseResult>} 注入。
	 */
	public static final VehicleLicenseParser INSTANCE = new VehicleLicenseParser();

	/**
	 * 车牌号：省/市汉字 + 字母 + 5~6 位字母数字（新能源 8 位也覆盖）
	 */
	private static final Pattern PLATE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}");
	/**
	 * VIN 车架号：17 位大写字母数字
	 */
	private static final Pattern VIN_PATTERN = Pattern.compile("[A-Z0-9]{17}");
	/**
	 * 日期：yyyy-MM-dd
	 */
	private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

	/**
	 * 工具类风格入口：直接传入 OCR 结果列表即可解析。
	 *
	 * @param results OCR 结果列表
	 * @return 结构化解析结果
	 */
	public static VehicleLicenseResult parse(List<PPOcrV6Result> results) {
		return INSTANCE.doParse(results);
	}

	/**
	 * {@link BaseStructuredParser} 接口实现，便于通过实例注入使用。
	 *
	 * @param results OCR 结果列表
	 * @return 结构化解析结果
	 */
	@Override
	public VehicleLicenseResult parseResults(List<PPOcrV6Result> results) {
		return doParse(results);
	}

	private VehicleLicenseResult doParse(List<PPOcrV6Result> results) {
		VehicleLicenseResult license = new VehicleLicenseResult();
		license.setPlateNo(LabelMatcher.labelOrFallback(LabelMatcher.matchValue(results, "号牌号码"), results, PLATE_PATTERN, "车牌", false));
		// 先按中文标签「所有人」定位；medium 模型可能缺失中文标签：
		// 1) 尝试英文别名「Owner」匹配；2) 再按版面布局兜底（车辆类型行下方、住址标签上方的最宽非标签文本）
		String owner = LabelMatcher.matchValue(results, "所有人");
		if (owner == null) {
			owner = LabelMatcher.matchValue(results, "Owner");
			if (owner != null) {
				log.info("行驶证解析：所有人 按英文标签 Owner fallback 命中 \"{}\"", owner);
			} else {
				owner = matchOwnerByLayoutFallback(results);
				if (owner != null) {
					log.info("行驶证解析：所有人 按版面布局 fallback 命中 \"{}\"", owner);
				}
			}
		}
		license.setOwner(owner);
		license.setVehicleType(LabelMatcher.matchValue(results, "车辆类型"));

		String vin = LabelMatcher.labelOrFallback(
			LabelMatcher.matchValue(results, "车辆识别代号"), results, VIN_PATTERN, "VIN", false);
		if (vin == null) {
			vin = matchVINFallback(results);
			if (vin != null) {
				log.info("行驶证解析：VIN 子串搜索兜底命中 \"{}\"", vin);
			}
		}
		license.setVin(vin);

		String issueDate = LabelMatcher.labelOrFallback(
			LabelMatcher.matchValue(results, "发证日期"), results, DATE_PATTERN, "发证日期", true);
		if (issueDate == null) {
			issueDate = matchDateFallback(results);
			if (issueDate != null) {
				log.info("行驶证解析：发证日期 子串搜索兜底命中 \"{}\"", issueDate);
			}
		}
		license.setIssueDate(issueDate);
		return license;
	}

	/**
	 * VIN 子串搜索兜底：在文本中查找 17 位大写字母数字序列，
	 * 处理 OCR 噪声（如 ".LL4WG44B8JL339900" 前导点号）。
	 *
	 * <p>遍历每个 OCR 结果文本，在其中用 {@link java.util.regex.Matcher#find()}
	 * 寻找 17 位字母数字子串，返回首个命中的子串；找不到返回 null。
	 */
	private static String matchVINFallback(List<PPOcrV6Result> results) {
		return LabelMatcher.matchSubstring(results, text -> {
			Matcher m = VIN_PATTERN.matcher(text);
			return m.find() ? m.group() : null;
		});
	}

	/**
	 * 发证日期子串搜索兜底：在文本中查找首个 yyyy-MM-dd 子串，
	 * 处理 OCR 把"注册日期+发证日期"识别成单一文本框的场景
	 * （如"2018-03-052018-03-05"）。
	 *
	 * <p>遍历每个 OCR 结果文本，在其中用 {@link java.util.regex.Matcher#find()}
	 * 寻找首个日期子串并返回；找不到返回 null。
	 */
	private static String matchDateFallback(List<PPOcrV6Result> results) {
		return LabelMatcher.matchSubstring(results, text -> {
			Matcher m = DATE_PATTERN.matcher(text);
			return m.find() ? m.group() : null;
		});
	}

	/**
	 * 所有人版面布局兜底：基于证件版面结构定位 owner 值。
	 *
	 * <p>行驶证版面结构：所有人行位于「车辆类型」行下方、「住址」标签行上方。
	 * 当两种语言的标签都找不到（如 medium 模型中文标签全漏检、
	 * 英文标签 OCR 片段无法被 contains 匹配）时，按上下锚点之间的
	 * y 带寻找最宽的非纯英文文本作为 owner。
	 *
	 * <p>找不到锚点或该 y 带内没有值候选时返回 null。
	 */
	private static String matchOwnerByLayoutFallback(List<PPOcrV6Result> results) {
		// 上锚：车辆类型行（标签或值的 y 最大值）
		int vehicleTypeBottom = Integer.MIN_VALUE;
		String[] vtCandidates = {"车辆类型", "VehicleType"};
		for (String lbl : vtCandidates) {
			PPOcrV6Result b = LabelMatcher.findLabelBox(results, lbl);
			if (b != null) {
				vehicleTypeBottom = Math.max(vehicleTypeBottom, LabelMatcher.maxY(b));
			}
		}
		if (vehicleTypeBottom == Integer.MIN_VALUE) {
			// 找不到车辆类型标签也试试用车辆类型的值（中文车型文本）做锚
			for (PPOcrV6Result r : results) {
				String t = r.text();
				if (!t.matches("[A-Za-z\\s]+") && (t.contains("轿车") || t.contains("客车") || t.contains("货车") || t.contains("车"))) {
					vehicleTypeBottom = Math.max(vehicleTypeBottom, LabelMatcher.maxY(r));
				}
			}
		}
		if (vehicleTypeBottom == Integer.MIN_VALUE) {
			return null;
		}

		// 下锚：住址标签（"住址"或其残缺"住"/"址"）的 y 最小值
		int addressTop = Integer.MAX_VALUE;
		String[] addrCandidates = {"住址", "住", "址", "Address", "Adder"};
		for (String lbl : addrCandidates) {
			PPOcrV6Result b = LabelMatcher.findLabelBox(results, lbl);
			if (b != null) {
				addressTop = Math.min(addressTop, LabelMatcher.minY(b));
			}
		}
		if (addressTop == Integer.MAX_VALUE) {
			// 也尝试通过住址的值文本（含"省/市/区/路/街/号"等地址关键词）定位
			for (PPOcrV6Result r : results) {
				String t = r.text();
				if (t.matches(".*[省市区县路街道号镇村].*")) {
					addressTop = Math.min(addressTop, LabelMatcher.minY(r));
				}
			}
		}
		if (addressTop == Integer.MAX_VALUE || addressTop <= vehicleTypeBottom) {
			return null;
		}

		// 在 owner y 带中寻找宽度最大的非纯英文、非残缺标签文本
		String best = null;
		int bestWidth = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) continue;
			// 跳过纯英文标签文本
			if (text.matches("[A-Za-z\\s.]+")) continue;
			// 跳过"所有人"/"Owner"的标签片段（单个字或短字符序列）
			if ("所有人".contains(text) || "Owner".contains(text) || "owner".contains(text)) continue;
			// y 范围必须与 owner 带重叠
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

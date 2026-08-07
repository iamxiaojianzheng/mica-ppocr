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

package net.dreamlu.mica.ai.ppocr.test;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 行驶证 OCR 结构化解析工具类。
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
 */
@Slf4j
@UtilityClass
public class VehicleLicenseParser {

	/**
	 * 值框与标签框允许的横向重叠容差（像素），用于容忍边界 1px 相接
	 * （如"发证日期"标签与值框共用 x=2063）。
	 */
	private static final int RIGHT_OVERLAP_TOLERANCE = 5;

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
	 * 从 OCR 结果中解析行驶证关键字段。
	 *
	 * <p>车牌/VIN/发证日期采用"标签定位优先、正则兜底"策略：标签匹配失败时，
	 * 按内容特征（车牌号 7~8 位、VIN 17 位、日期 yyyy-MM-dd）扫描全部结果。
	 * 车辆类型为自由中文文本无固定格式，仅标签定位，不做正则兜底。
	 *
	 * @param results OCR 结果列表
	 * @return 结构化解析结果
	 */
	public static VehicleLicenseResult parse(List<PPOcrV6Result> results) {
	   VehicleLicenseResult license = new VehicleLicenseResult();
	   license.setPlateNo(labelOrFallback(matchValue(results, "号牌号码"), results, PLATE_PATTERN, "车牌", false));
	   license.setOwner(matchValue(results, "所有人"));
	   license.setVehicleType(matchValue(results, "车辆类型"));

	   String vin = labelOrFallback(matchValue(results, "车辆识别代号"), results, VIN_PATTERN, "VIN", false);
	   if (vin == null) {
	    vin = matchVINFallback(results);
	    if (vin != null) {
	     log.info("行驶证解析：VIN 子串搜索兜底命中 \"{}\"", vin);
	    }
	   }
	   license.setVin(vin);

	   license.setIssueDate(labelOrFallback(matchValue(results, "发证日期"), results, DATE_PATTERN, "发证日期", true));
	   return license;
	  }

	/**
	 * 按标签匹配值框文本。
	 *
	 * <p>标签查找支持 OCR 残缺标签：优先取"完整包含标签"的框，
	 * 没有时退而取"标签包含其文本"的框（如"所有人"被识别成"所"）。
	 *
	 * @param results OCR 结果列表
	 * @param label   标签文本（如"号牌号码"）
	 * @return 匹配到的值框文本，找不到返回 null
	 */
	private static String matchValue(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result labelBox = findLabelBox(results, label);
		if (labelBox == null) {
			log.warn("行驶证解析：未找到标签 \"{}\"，该字段置 null", label);
			return null;
		}

		double labelRight = maxX(labelBox);
		int labelMinY = minY(labelBox);
		int labelMaxY = maxY(labelBox);

		String best = null;
		int bestX = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			int x0 = minX(r);
			// 值框左边缘须在标签右边缘右侧；容差容忍边界 1px 相接（如"发证日期"与值框共用 x=2063）
			if (x0 <= labelRight - RIGHT_OVERLAP_TOLERANCE) {
				continue;
			}
			if (maxY(r) < labelMinY || minY(r) > labelMaxY) {
				continue; // y 范围必须与标签框重叠（同一行）
			}
			if (x0 < bestX) {
				bestX = x0;
				best = r.text();
			}
		}
		if (best == null) {
			log.warn("行驶证解析：标签 \"{}\" 未匹配到值框，该字段置 null", label);
		}
		return best;
	}

	/**
	 * 查找标签框，支持 OCR 残缺标签。
	 *
	 * @param results OCR 结果列表
	 * @param label   标签文本
	 * @return 标签框；取文本最长者（最接近完整标签），找不到返回 null
	 */
	private static PPOcrV6Result findLabelBox(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result best = null;
		int bestLen = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) {
				continue;
			}
			// 完整包含标签（text.contains(label)），或标签包含 OCR 文本（残缺标签）
			if (text.contains(label) || label.contains(text)) {
				if (text.length() > bestLen) {
					bestLen = text.length();
					best = r;
				}
			}
		}
		return best;
	}

	/**
	 * 按内容特征正则扫描全部结果。
	 *
	 * @param results OCR 结果列表
	 * @param pattern 特征正则（整串匹配）
	 * @param last    取最后一个匹配（true）还是第一个（false）
	 * @return 匹配文本，找不到返回 null
	 */
	private static String matchPattern(List<PPOcrV6Result> results, Pattern pattern, boolean last) {
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
	 * VIN 子串搜索兜底：在文本中查找 17 位大写字母数字序列，
	 * 处理 OCR 噪声（如 ".LL4WG44B8JL339900" 前导点号）。
	 */
	private static String matchVINFallback(List<PPOcrV6Result> results) {
		Pattern p = Pattern.compile("[A-Z0-9]{17}");
		for (PPOcrV6Result r : results) {
			java.util.regex.Matcher m = p.matcher(r.text());
			if (m.find()) {
				return m.group();
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
	private static String labelOrFallback(String labelValue, List<PPOcrV6Result> results, Pattern pattern, String fieldName, boolean last) {
		if (labelValue != null) {
			if (pattern.matcher(labelValue).matches()) {
				return labelValue; // 标签定位 + 格式校验通过
			}
			log.warn("行驶证解析：{} 位置匹配 \"{}\" 格式异常，改走正则兜底", fieldName, labelValue);
		}
		String fallback = matchPattern(results, pattern, last);
		if (fallback != null) {
			log.info("行驶证解析：{} 正则兜底命中 \"{}\"", fieldName, fallback);
		}
		return fallback;
	}

	private static int minX(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) {
			min = Math.min(min, p[0]);
		}
		return min;
	}

	private static int maxX(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) {
			max = Math.max(max, p[0]);
		}
		return max;
	}

	private static int minY(PPOcrV6Result r) {
		int min = Integer.MAX_VALUE;
		for (int[] p : r.box()) {
			min = Math.min(min, p[1]);
		}
		return min;
	}

	private static int maxY(PPOcrV6Result r) {
		int max = Integer.MIN_VALUE;
		for (int[] p : r.box()) {
			max = Math.max(max, p[1]);
		}
		return max;
	}
}

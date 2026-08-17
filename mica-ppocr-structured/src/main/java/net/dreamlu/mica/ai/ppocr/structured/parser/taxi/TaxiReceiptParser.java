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

package net.dreamlu.mica.ai.ppocr.structured.parser.taxi;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 出租车票 OCR 结构化解析器。
 *
 * <p>兼容全国各地出租车票版式（套打偏移、油墨污损等）；字段对齐百度 OCR 出租车票接口。
 *
 * <p>策略概览：
 * <ul>
 *   <li><b>发票代码 / 号码</b>：分别按 12 位与 8 位纯数字正则兜底；</li>
 *   <li><b>车牌号</b>：标签定位（"车牌号"/"车号"）+ {@code [京津沪渝...]} + 字母 + 数字 5~6 位 兜底；</li>
 *   <li><b>日期</b>：标签定位（"日期"/"开票日期"）+ 日期正则兜底；</li>
 *   <li><b>上下车时间</b>：标签定位（"上车时间"/"下车时间"）+ HH:mm 正则兜底；</li>
 *   <li><b>里程</b>：标签定位（"里程"）+ 数字 + 公里 兜底；</li>
 *   <li><b>金额四类</b>：标签定位 + {@code ¥?\d+.\d{2}} 正则兜底；</li>
 *   <li><b>开票城市</b>：标签定位（"开票城市"）+ 中文城市名兜底。</li>
 * </ul>
 *
 * <p>输出结果会填充 {@code TaxiReceiptResult#getRawResults()} 与
 * {@code TaxiReceiptResult#getFieldBoxes()}，便于页面高亮。
 */
@Slf4j
public class TaxiReceiptParser extends BaseStructuredParser<TaxiReceiptResult> {

	// ========================================================================
	// 正则常量
	// ========================================================================

	/** 发票代码：12 位纯数字。 */
	private static final Pattern INVOICE_CODE_PATTERN = Pattern.compile("\\d{12}");

	/** 发票号码：8 位纯数字。 */
	private static final Pattern INVOICE_NO_PATTERN = Pattern.compile("\\d{8}");

	/** 车牌号：省份简称 + 字母 + 5~6 位字母数字。 */
	private static final Pattern PLATE_NUMBER_PATTERN = Pattern.compile(
		"[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}");

	/** 日期：yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd / yyyy年MM月dd日。 */
	private static final Pattern DATE_PATTERN = Pattern.compile(
		"\\d{4}[-./年]\\d{1,2}[-./月]\\d{1,2}日?");

	/** 时间：HH:mm（24 小时制）。 */
	private static final Pattern TIME_PATTERN = Pattern.compile(
		"([01]?\\d|2[0-3]):[0-5]\\d");

	/** 里程：纯数字 + 小数点 + 公里（兼容 "12.5km"、"14.2公里"）。 */
	private static final Pattern MILEAGE_PATTERN = Pattern.compile(
		"\\d+(?:\\.\\d)?\\s*(?:km|公里)");

	/** 纯数字里程（兜底）。 */
	private static final Pattern MILEAGE_NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d)?");

	/** 金额：¥/￥ + 数字 + 两位小数。 */
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
		"[¥￥]?\\s*\\d+(?:\\.\\d{1,2})?\\s*元?");

	/** 中文城市名：2~10 字。 */
	private static final Pattern CITY_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,10}");

	/** 中国汽车牌照省份简称（避免误识别）。 */
	private static final Set<String> PLATE_PROVINCES = Set.of(
		"京", "津", "沪", "渝", "冀", "豫", "云", "辽", "黑", "湘",
		"皖", "鲁", "新", "苏", "浙", "赣", "鄂", "桂", "甘", "晋",
		"蒙", "陕", "吉", "闽", "贵", "粤", "川", "青藏", "宁", "琼"
	);

	// ========================================================================
	// 入口
	// ========================================================================

	/**
	 * 构造出租车票解析器，绑定推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎；可为 null（仅在仅调用 {@link #parseResults(List)} 时）
	 */
	public TaxiReceiptParser(PPOcrV6Engine engine) {
		super(engine);
	}

	@Override
	public TaxiReceiptResult parseResults(List<PPOcrV6Result> results) {
		TaxiReceiptResult r = new TaxiReceiptResult();
		r.setRawResults(new ArrayList<>(results));

		// 票号
		r.setInvoiceCode(parseInvoiceCode(results));
		r.setInvoiceNo(parseInvoiceNo(results));

		// 行程
		r.setPlateNumber(parsePlateNumber(results));
		r.setDate(parseDate(results));
		r.setBoardingTime(parseTime(results, "上车时间", "上客时间", "乘"));
		r.setAlightingTime(parseTime(results, "下车时间", "下客时间", "落"));
		r.setMileage(parseMileage(results));

		// 金额
		r.setAmount(parseAmount(results, "金额"));
		r.setFuelSurcharge(parseAmount(results, "燃油附加费"));
		r.setBookingFee(parseAmount(results, "叫车服务费"));
		r.setTotalAmount(parseAmount(results, "总金额"));

		// 其他
		r.setCity(parseCity(results));

		return r;
	}

	// ========================================================================
	// 票号
	// ========================================================================

	private static String parseInvoiceCode(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "发票代码");
		if (v != null) {
			Matcher m = INVOICE_CODE_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return LabelMatcher.matchPattern(results, INVOICE_CODE_PATTERN, false);
	}

	private static String parseInvoiceNo(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "发票号码");
		if (v != null) {
			Matcher m = INVOICE_NO_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return LabelMatcher.matchPattern(results, INVOICE_NO_PATTERN, false);
	}

	// ========================================================================
	// 行程
	// ========================================================================

	private static String parsePlateNumber(List<PPOcrV6Result> results) {
		// 1) 标签定位（"车牌号"/"车号"）
		String v = LabelMatcher.matchValueFromPrefix(results, "车牌号");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "车号");
		}
		if (v != null) {
			Matcher m = PLATE_NUMBER_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		// 2) 兜底：扫所有框，找省份简称开头 + 字母 + 5~6 位字母数字
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (text.length() < 6 || text.length() > 8) continue;
			if (!PLATE_PROVINCES.contains(text.substring(0, 1))) continue;
			Matcher m = PLATE_NUMBER_PATTERN.matcher(text);
			if (m.matches()) {
				log.debug("出租车票解析：车牌号按正则兜底 \"{}\"", text);
				return text;
			}
		}
		return null;
	}

	private static String parseDate(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "日期");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "开票日期");
		}
		if (v != null) {
			Matcher m = DATE_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return LabelMatcher.matchPattern(results, DATE_PATTERN, false);
	}

	/**
	 * 通用时间标签匹配：依次尝试多个标签 + 兜底。
	 */
	private static String parseTime(List<PPOcrV6Result> results, String... labels) {
		for (String label : labels) {
			String v = LabelMatcher.matchValueFromPrefix(results, label);
			if (v != null) {
				Matcher m = TIME_PATTERN.matcher(v);
				if (m.find()) {
					return m.group();
				}
			}
		}
		// 兜底：扫所有框（同一时间只能取首个，否则会和"上下车"重复）
		return null;
	}

	private static String parseMileage(List<PPOcrV6Result> results) {
		// 1) 标签定位（"里程"），格式如 "14.2km" / "14.2 公里"
		String v = LabelMatcher.matchValueFromPrefix(results, "里程");
		if (v != null) {
			Matcher m = MILEAGE_PATTERN.matcher(v);
			if (m.find()) {
				String hit = m.group();
				// 剥 "km" / "公里" 后缀
				Matcher num = MILEAGE_NUMBER_PATTERN.matcher(hit);
				if (num.find()) {
					return num.group();
				}
			}
			// 标签值是纯数字
			String stripped = v.replaceAll("[^0-9.]", "");
			if (!stripped.isEmpty()) {
				return stripped;
			}
		}
		// 2) 兜底：扫所有框匹配 "数字km/公里"
		String hit = LabelMatcher.matchPattern(results, MILEAGE_PATTERN, false);
		if (hit != null) {
			Matcher num = MILEAGE_NUMBER_PATTERN.matcher(hit);
			if (num.find()) {
				return num.group();
			}
		}
		return null;
	}

	// ========================================================================
	// 金额
	// ========================================================================

	/**
	 * 通用金额标签匹配：标签定位 + 正则兜底。
	 */
	private static String parseAmount(List<PPOcrV6Result> results, String primaryLabel) {
		String v = LabelMatcher.matchValueFromPrefix(results, primaryLabel);
		if (v != null) {
			Matcher m = AMOUNT_PATTERN.matcher(v);
			if (m.find()) {
				String hit = m.group();
				// 优先纯数字（去 ¥/￥/元/空格）
				String digits = hit.replaceAll("[^0-9.]", "");
				if (!digits.isEmpty()) {
					return digits;
				}
			}
		}
		// 兜底（仅 amount 类；金额、燃油附加费、叫车服务费、总金额）
		// 不做全局正则兜底，避免错抓其它金额
		return null;
	}

	// ========================================================================
	// 其他
	// ========================================================================

	private static String parseCity(List<PPOcrV6Result> results) {
		// 1) 标签定位
		String v = LabelMatcher.matchValueFromPrefix(results, "开票城市");
		if (v != null) {
			Matcher m = CITY_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}
}
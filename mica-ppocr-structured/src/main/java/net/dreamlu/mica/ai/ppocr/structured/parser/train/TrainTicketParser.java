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

package net.dreamlu.mica.ai.ppocr.structured.parser.train;

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
 * 火车票 OCR 结构化解析器。
 *
 * <p>兼容中国铁路纸质票（蓝/红票）与电子客票版式；字段对齐百度 OCR 火车票接口。
 *
 * <p>策略概览：
 * <ul>
 *   <li><b>车次号</b>：纯正则兜底（{@code [GDCZTKL]\d{1,4}}），不限位置；</li>
 *   <li><b>车票号 / 发票号 / 电子客票号</b>：按长度优先的纯数字正则兜底（10 / 20 / 25 位）；</li>
 *   <li><b>出发日期 / 时间</b>：标签定位（"出发日期"/"出发时间"/"时间"）；</li>
 *   <li><b>金额</b>：标签定位（"车票金额"/"票价"/"金额"）+ {@code ￥\d+.\d{2}元?} 正则；</li>
 *   <li><b>始发站 / 到达站</b>：标签定位（"始发站"/"出发站" 与 "到达站"/"目的站"）；</li>
 *   <li><b>座位号</b>：正则 {@code \d{2}车\d{1,3}[A-Z]?号}；</li>
 *   <li><b>席别</b>：标签定位（"席别"/"座位类型"），兜底中文匹配；</li>
 *   <li><b>乘客姓名 / 身份证</b>：标签定位 + 正则兜底；</li>
 *   <li><b>其他辅助字段</b>：标签定位，找不到时返回 null。</li>
 * </ul>
 *
 * <p>输出结果会填充 {@code TrainTicketResult#getRawResults()} 与
 * {@code TrainTicketResult#getFieldBoxes()}，便于页面高亮。
 */
@Slf4j
public class TrainTicketParser extends BaseStructuredParser<TrainTicketResult> {

	// ========================================================================
	// 正则常量
	// ========================================================================

	/**
	 * 车次号：高铁/动车 G/D、城际 C、直达 Z、特快 T、快速 K、普快/临时/旅游 Y/L。
	 * 兼容 OCR 误识别（如 G1234 → "G1234" 或 "G1234."）。
	 */
	private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("[GDCZTKYL]\\d{1,4}");

	/**
	 * 车票号：10 位纯数字（部分票有分隔空格/短横线）。
	 */
	private static final Pattern TICKET_NO_PATTERN = Pattern.compile("\\d{10}");

	/** 发票号码：20 位纯数字。 */
	private static final Pattern INVOICE_NO_PATTERN = Pattern.compile("\\d{20}");

	/** 电子客票号：25 位纯数字。 */
	private static final Pattern ETICKET_NO_PATTERN = Pattern.compile("\\d{25}");

	/**
	 * 日期：yyyy年MM月dd日 / yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd。
	 */
	private static final Pattern DATE_PATTERN = Pattern.compile(
		"\\d{4}[-./年]\\d{1,2}[-./月]\\d{1,2}日?");

	/** 时间：HH:mm（24 小时制）。 */
	private static final Pattern TIME_PATTERN = Pattern.compile(
		"([01]?\\d|2[0-3]):[0-5]\\d");

	/**
	 * 金额：支持 ￥ / ¥ 前缀，"元" 后缀，保留两位小数。
	 */
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
		"[¥￥]\\s*\\d+(?:\\.\\d{1,2})?\\s*元?");

	/**
	 * 座位号：02车12A号 / 02车12号 / 02车12A / 5车12C。
	 * 兼容 OCR 把 "号" 漏识别或末尾多余空格。
	 */
	private static final Pattern SEAT_NUMBER_PATTERN = Pattern.compile(
		"\\d{1,3}车\\d{1,4}[A-Z]?号?");

	/**
	 * 身份证号：18 位（末位 X 支持）。
	 * 兼容星号脱敏：先剥星号再校验位数。
	 */
	private static final Pattern ID_NUMBER_PATTERN = Pattern.compile(
		"\\d{17}[\\dXx]");

	/** 4~6 字中文姓名（OCR 残缺兜底：2~6 字）。 */
	private static final Pattern NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}");

	/** 席别关键字。 */
	private static final List<String> SEAT_CLASS_KEYWORDS = List.of(
		"商务座", "一等座", "二等座", "特等座",
		"软卧", "硬卧", "高级软卧",
		"软座", "硬座",
		"一等卧", "二等卧"
	);

	/** 改签标识关键字。 */
	private static final List<String> CHANGED_FLAG_KEYWORDS = List.of(
		"始发改签", "退票", "改签"
	);

	// ========================================================================
	// 入口
	// ========================================================================

	/**
	 * 构造火车票解析器，绑定推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎；可为 null（仅在仅调用 {@link #parseResults(List)} 时）
	 */
	public TrainTicketParser(PPOcrV6Engine engine) {
		super(engine);
	}

	@Override
	public TrainTicketResult parseResults(List<PPOcrV6Result> results) {
		TrainTicketResult r = new TrainTicketResult();
		r.setRawResults(new ArrayList<>(results));

		// 行程
		r.setDeparture(parseDeparture(results));
		r.setArrival(parseArrival(results));
		r.setTrainNumber(parseTrainNumber(results));
		parseDepartureDateTime(r, results);
		r.setSeatNumber(parseSeatNumber(results));
		r.setSeatClass(parseSeatClass(results));

		// 乘客
		r.setPassengerName(parsePassengerName(results));
		r.setIdNumber(parseIdNumber(results));

		// 金额
		r.setAmount(parseAmount(results, "车票金额"));
		r.setAmountExcludingTax(parseAmountExcludingTax(results));

		// 票号
		r.setTicketNo(parseTicketNo(results));
		r.setInvoiceNo(parseInvoiceNo(results));
		r.setETicketNo(parseETicketNo(results));

		// 其他
		r.setInvoiceDate(parseInvoiceDate(results));
		r.setSellStation(parseSellStation(results));
		r.setSerialNumber(parseSerialNumber(results));
		r.setChangedFlag(parseChangedFlag(results));

		return r;
	}

	// ========================================================================
	// 行程：始发/到达/车次/日期/时间/座位/席别
	// ========================================================================

	private static String parseDeparture(List<PPOcrV6Result> results) {
		// 1) 标签 "始发站" / "出发站"（兼容标签独立框 + 合并框"始发站 北京南站"）
		String v = LabelMatcher.matchValueFromPrefix(results, "始发站");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "出发站");
		}
		if (v != null) {
			return cleanStation(v);
		}
		// 2) 兜底：图片最左侧顶部的中文站名（≥3 字）
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (text.endsWith("站") && text.length() >= 3 && LabelMatcher.minX(r) < 300) {
				String station = text.substring(0, text.length() - 1);
				if (station.matches("[\\u4e00-\\u9fa5]{2,}")) {
					log.debug("火车票解析：始发站按位置兜底 \"{}\"", text);
					return station;
				}
			}
		}
		return null;
	}

	private static String parseArrival(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "到达站");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "目的站");
		}
		if (v != null) {
			return cleanStation(v);
		}
		// 兜底：右侧顶部的中文站名
		int imgMaxX = 0;
		for (PPOcrV6Result r : results) {
			imgMaxX = Math.max(imgMaxX, LabelMatcher.maxX(r));
		}
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (text.endsWith("站") && text.length() >= 3) {
				if (LabelMatcher.minX(r) > imgMaxX * 0.5) {
					String station = text.substring(0, text.length() - 1);
					if (station.matches("[\\u4e00-\\u9fa5]{2,}")) {
						log.debug("火车票解析：到达站按位置兜底 \"{}\"", text);
						return station;
					}
				}
			}
		}
		return null;
	}

	private static String parseTrainNumber(List<PPOcrV6Result> results) {
		// 1) 标签 "车次" / "车次号"（兼容独立框 + 合并框"车次 G1234"）
		String[] labels = {"车次", "车次号"};
		for (String label : labels) {
			String v = LabelMatcher.matchValueFromPrefix(results, label);
			if (v != null) {
				Matcher m = TRAIN_NUMBER_PATTERN.matcher(v);
				if (m.find()) {
					return m.group();
				}
			}
		}
		// 2) 兜底：扫所有框匹配车次正则，取最短（避免误识别长字符串）
		String best = null;
		for (PPOcrV6Result r : results) {
			Matcher m = TRAIN_NUMBER_PATTERN.matcher(r.text());
			while (m.find()) {
				String hit = m.group();
				if (best == null || hit.length() < best.length()) {
					best = hit;
				}
			}
		}
		if (best != null) {
			log.debug("火车票解析：车次按正则兜底 \"{}\"", best);
		}
		return best;
	}

	/**
	 * 同时处理出发日期与时间。
	 */
	private void parseDepartureDateTime(TrainTicketResult r, List<PPOcrV6Result> results) {
		// 1) 标签 "出发日期" / "乘车日期" + 标签 "出发时间" / "乘车时间" / "时间"
		LabeledMatch dateMatch = LabelMatcher.matchValueWithBox(results, "出发日期");
		if (dateMatch.value() == null) {
			dateMatch = LabelMatcher.matchValueWithBox(results, "乘车日期");
		}
		if (dateMatch.value() == null) {
			dateMatch = LabelMatcher.matchValueWithBox(results, "日期");
		}
		// 2) 兜底：扫所有框找日期正则
		if (dateMatch.value() == null) {
			dateMatch = LabelMatcher.matchPatternWithBox(results, DATE_PATTERN, false);
		}
		String date = null;
		if (dateMatch.value() != null) {
			Matcher m = DATE_PATTERN.matcher(dateMatch.value());
			if (m.find()) {
				date = m.group();
			}
		}
		r.setDepartureDate(date);
		LabelMatcher.applyFieldBox(r, "departureDate", dateMatch);

		// 时间
		LabeledMatch timeMatch = LabelMatcher.matchValueWithBox(results, "出发时间");
		if (timeMatch.value() == null) {
			timeMatch = LabelMatcher.matchValueWithBox(results, "乘车时间");
		}
		if (timeMatch.value() == null) {
			timeMatch = LabelMatcher.matchValueWithBox(results, "时间");
		}
		if (timeMatch.value() == null) {
			timeMatch = LabelMatcher.matchPatternWithBox(results, TIME_PATTERN, false);
		}
		String time = null;
		if (timeMatch.value() != null) {
			Matcher m = TIME_PATTERN.matcher(timeMatch.value());
			if (m.find()) {
				time = m.group();
			}
		}
		r.setDepartureTime(time);
		LabelMatcher.applyFieldBox(r, "departureTime", timeMatch);
	}

	private static String parseSeatNumber(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValue(results, "座位号");
		if (v != null) {
			Matcher m = SEAT_NUMBER_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		// 兜底：扫所有框
		return LabelMatcher.matchPattern(results, SEAT_NUMBER_PATTERN, false);
	}

	private static String parseSeatClass(List<PPOcrV6Result> results) {
		// 1) 标签 "席别" / "座位类型"
		String v = LabelMatcher.matchValue(results, "席别");
		if (v == null) {
			v = LabelMatcher.matchValue(results, "座位类型");
		}
		if (v != null) {
			for (String kw : SEAT_CLASS_KEYWORDS) {
				if (v.contains(kw)) {
					return kw;
				}
			}
			// 标签完整就返回
			return v;
		}
		// 2) 兜底：扫所有框找席别关键字
		for (PPOcrV6Result r : results) {
			String text = r.text();
			for (String kw : SEAT_CLASS_KEYWORDS) {
				if (text.contains(kw)) {
					log.debug("火车票解析：席别按关键字兜底 \"{}\"", kw);
					return kw;
				}
			}
		}
		return null;
	}

	// ========================================================================
	// 乘客
	// ========================================================================

	private static String parsePassengerName(List<PPOcrV6Result> results) {
		// 1) 标签定位（兼容独立框 + 合并框"姓名 张三"）
		String v = LabelMatcher.matchValueFromPrefix(results, "姓名");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "乘客姓名");
		}
		if (v != null) {
			Matcher m = NAME_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
			return v;
		}
		// 2) 兜底：扫所有框找 2~4 字中文（避免 OCR 噪声框混入）
		//    排除：含站/座/车/票/卧/元/￥等票面关键字；
		//    排除：纯数字 / 含英文 / 含符号；
		//    排除：含乱码（OCR 噪声框常见）的框 —— 置信度 < 0.5 视为噪声。
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (!NAME_PATTERN.matcher(text).matches()) continue;
			if (text.contains("站") || text.contains("座") || text.contains("车")
				|| text.contains("票") || text.contains("卧") || text.contains("元")
				|| text.contains("￥") || text.contains("¥")) continue;
			if (text.matches(".*[A-Za-z].*") || text.matches(".*\\d.*")) continue;
			if (r.score() < 0.5f) continue;
			log.debug("火车票解析：乘客姓名按中文框兜底 \"{}\" (score={})", text, r.score());
			return text;
		}
		return null;
	}

	private static String parseIdNumber(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValue(results, "身份证号");
		if (v == null) {
			v = LabelMatcher.matchValue(results, "证件号码");
		}
		if (v == null) {
			v = LabelMatcher.matchValue(results, "公民身份号码");
		}
		if (v != null) {
			Matcher m = ID_NUMBER_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
			// 兼容脱敏：先剥星号再尝试匹配
			String stripped = v.replaceAll("[*\\s]", "");
			if (stripped.length() == 18) {
				return stripped;
			}
		}
		return null;
	}

	// ========================================================================
	// 金额
	// ========================================================================

	private static String parseAmount(List<PPOcrV6Result> results, String primaryLabel) {
		// 1) 标签定位（兼容独立框 + 合并框"车票金额 ￥26.00元"）
		String v = LabelMatcher.matchValueFromPrefix(results, primaryLabel);
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "票价");
		}
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "金额");
		}
		if (v != null) {
			Matcher m = AMOUNT_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		// 2) 兜底：扫所有框
		return LabelMatcher.matchPattern(results, AMOUNT_PATTERN, false);
	}

	/**
	 * 不含税金额：标签 "不含税金额" / "税前金额"。
	 */
	private static String parseAmountExcludingTax(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "不含税金额");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "税前金额");
		}
		if (v != null) {
			Matcher m = AMOUNT_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}

	// ========================================================================
	// 票号
	// ========================================================================

	private static String parseTicketNo(List<PPOcrV6Result> results) {
		// 1) 标签 "车票号"
		String v = LabelMatcher.matchValue(results, "车票号");
		if (v == null) {
			v = LabelMatcher.matchValue(results, "票号");
		}
		if (v != null) {
			Matcher m = TICKET_NO_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
			// 兼容带分隔符
			String stripped = v.replaceAll("[\\s-]", "");
			if (stripped.length() == 10 && stripped.matches("\\d{10}")) {
				return stripped;
			}
		}
		// 2) 兜底：扫所有 10 位连续数字
		return LabelMatcher.matchPattern(results, TICKET_NO_PATTERN, false);
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

	private static String parseETicketNo(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValue(results, "电子客票号");
		if (v != null) {
			Matcher m = ETICKET_NO_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return LabelMatcher.matchPattern(results, ETICKET_NO_PATTERN, false);
	}

	// ========================================================================
	// 其他
	// ========================================================================

	private static String parseInvoiceDate(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValueFromPrefix(results, "开票日期");
		if (v != null) {
			Matcher m = DATE_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}

	private static String parseSellStation(List<PPOcrV6Result> results) {
		return LabelMatcher.matchValue(results, "售站");
	}

	private static String parseSerialNumber(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValue(results, "序列号");
		if (v != null) {
			// 序列号通常是 12~20 位数字字母混合
			String cleaned = v.replaceAll("\\s+", "");
			return cleaned;
		}
		return null;
	}

	private static String parseChangedFlag(List<PPOcrV6Result> results) {
		// 标签 "标识" / "改签标识"
		String v = LabelMatcher.matchValueFromPrefix(results, "标识");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "改签标识");
		}
		if (v != null) {
			for (String kw : CHANGED_FLAG_KEYWORDS) {
				if (v.contains(kw)) {
					return kw;
				}
			}
		}
		// 兜底：扫所有框
		for (PPOcrV6Result r : results) {
			String text = r.text();
			for (String kw : CHANGED_FLAG_KEYWORDS) {
				if (text.contains(kw)) {
					log.debug("火车票解析：改签标识按关键字兜底 \"{}\"", kw);
					return kw;
				}
			}
		}
		return null;
	}

	// ========================================================================
	// 工具
	// ========================================================================

	/**
	 * 清理站名：去掉 OCR 误识别的尾随字符（如 "北京南站 " → "北京南"）。
	 */
	private static String cleanStation(String v) {
		String t = v.trim();
		// OCR 偶尔在站名后多识别字符，按 "X站" 规则截取
		int stationIdx = t.indexOf("站");
		if (stationIdx > 0) {
			return t.substring(0, stationIdx);
		}
		// 没 "站" 字时直接返回清理后的串
		return t;
	}
}
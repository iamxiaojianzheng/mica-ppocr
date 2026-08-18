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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 火车票 OCR 结构化解析器。
 *
 * <p>兼容中国铁路纸质票（蓝/红票）与电子客票版式；字段对齐百度 OCR 火车票接口。
 *
 * <p>策略概览：
 * <ul>
 *   <li><b>始发 / 到达站</b>：标签定位 + 全图顶部兜底（放宽 minX 阈值到 500 像素，兼容 OCR 框偏左）；</li>
 *   <li><b>车次</b>：标签定位 + 全图正则兜底（[GDCZTKYL]\d{1,4}，跳过"原票"上下文避免误识别 L 开头补票号）；</li>
 *   <li><b>出发日期 / 时间</b>：从合并框"YYYY年MM月DD日HH:MM开"统一切分；</li>
 *   <li><b>金额</b>：标签定位 + 关键字兜底 + 纯数字/¥ 金额正则；</li>
 *   <li><b>票号</b>：放宽到 7-10 位（实际纸质票号多为 7-8 位），与电子客票 20/25 位不冲突；</li>
 *   <li><b>乘客姓名</b>：标签定位 + 合并框（身份证+姓名）剥值；</li>
 *   <li><b>身份证</b>：标签定位 + 部分脱敏保留（业务可自行决定是否使用）；</li>
 *   <li><b>售站</b>：标签定位 + 票面底部"XX售"模式兜底；</li>
 *   <li><b>席别</b>：标签定位 + 关键字兜底；</li>
 *   <li><b>改签标识</b>：标签定位 + 关键字兜底。</li>
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
	 * 兼容 OCR 误识别：把 0 误识别为 O/I/L（"GO000" → "G0000"）。
	 * 长度限制 1-4 位数字。
	 */
	private static final Pattern TRAIN_NUMBER_PATTERN = Pattern.compile("[GDCZTKYL][\\dOIl]{1,4}");

	/**
	 * 纯数字车次正则（用于 GO000 兼容：OCR 把 G 后面的 0 误识别为 O）。
	 * 匹配 "GO000" 中的 "G0000"（O 视作 0）。
	 */
	private static final Pattern TRAIN_NUMBER_DIGIT_PATTERN = Pattern.compile("[GDCZTKYL]\\d{1,4}");

	/**
	 * 车票号：7-10 位纯数字。覆盖 E014470 / R093443 / U028534 等 7 位票号。
	 * 部分票有分隔空格/短横线，统一归一化。
	 */
	private static final Pattern TICKET_NO_PATTERN = Pattern.compile("\\d{7,10}");

	/** 发票号码：20 位纯数字。 */
	private static final Pattern INVOICE_NO_PATTERN = Pattern.compile("\\d{20}");

	/** 电子客票号：25 位纯数字。 */
	private static final Pattern ETICKET_NO_PATTERN = Pattern.compile("\\d{25}");

	/**
	 * 日期：yyyy年MM月dd日 / yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd。
	 * 宽松分隔符（含 [:：] 容忍 OCR 噪声）。
	 */
	private static final Pattern DATE_PATTERN = Pattern.compile(
		"\\d{2,4}[-./年:：]\\d{1,2}[-./月:：]\\d{1,2}日?");

	/**
	 * HH:mm 完整片段（不分组）。
	 */
	private static final String HHMM = "(?:[01]?\\d|2[0-3]):[0-5]\\d";

	/** 时间：HH:mm（24 小时制）。 */
	private static final Pattern TIME_PATTERN = Pattern.compile(HHMM);

	/**
	 * 金额：支持 ￥ / ¥ 前缀，"元" 后缀，保留两位小数。
	 * 容许 OCR 噪声 "-" 当小数点。
	 */
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
		"[¥￥]\\s*\\d+(?:[.-]\\d{1,2})?\\s*元?");

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

	/**
	 * 站名模式：中文地名 + 方位 / 行政区划后缀，或常见无后缀城市名（京/沪/津/渝/港/澳等直辖市）。
	 * 用于在姓名兜底中排除"平顶山西""上海""北京"等不带"站"字的站名。
	 */
	private static final Pattern STATION_SUFFIX_PATTERN = Pattern.compile(
		"[\\u4e00-\\u9fa5]+(?:西|东|南|北|市|县|州|区|省|旗|镇|乡|村|海|口|津|渝|港|澳|京|沪)$");

	/**
	 * 售站兜底正则：票面底部"XX售"或"XX局XX售"（如"天津售"、"银川售"、"济局青客补"）。
	 */
	private static final Pattern SELL_STATION_PATTERN = Pattern.compile(
		"([\\u4e00-\\u9fa5]{2,8})[售$]");

	/**
	 * 机构名特征字：包含其一即视为非人名。
	 * 覆盖常见票面/官方机构关键词（国家税务总局、铁路局、汽车运管所、客运段等）。
	 */
	private static final Set<Character> INSTITUTION_KEY_CHARS = Set.of(
		'局', '司', '所', '院', '处', '部', '厅', '署',
		'税', '运', '铁', '邮', '公', '证', '发', '联',
		'会', '学', '校', '厂', '店', '馆', '场',
		'总', '队', '股', '行', '团', '组', '社');

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

	/**
	 * 出发日期+时间合并框模式：2014年09月09日15:52开。
	 * 优先匹配完整的"日期+时间+开"格式。
	 */
	private static final Pattern DATE_TIME_OPEN_PATTERN = Pattern.compile(
		"(\\d{4}[-./年:：]\\d{1,2}[-./月:：]\\d{1,2}日?)" + HHMM + "开?");

	/**
	 * 仅日期+时间（无"开"字）的合并框模式。
	 */
	private static final Pattern DATE_TIME_PATTERN = Pattern.compile(
		"(\\d{4}[-./年:：]\\d{1,2}[-./月:：]\\d{1,2}日?)" + "\\s*" + HHMM);

	/**
	 * 噪声上下文词：含其一即跳过（避免误识别为车次）。
	 * 典型：原票、原票号、补票（火车票"原票：L0956"格式不应作为车次）。
	 */
	private static final Set<String> TRAIN_NUMBER_NOISE_KEYWORDS = Set.of(
		"原票", "补", "越站", "事由", "加收");

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

	/**
	 * 始发站解析：
	 * <ol>
	 *   <li>标签 "始发站" / "出发站"（兼容合并框）；</li>
	 *   <li>兜底：票面顶部 y≤400、minX≤500 的"X站"框（放宽原 300 阈值以适配实际票面）。</li>
	 * </ol>
	 */
	private static String parseDeparture(List<PPOcrV6Result> results) {
		// 1) 标签
		String v = LabelMatcher.matchValueFromPrefix(results, "始发站");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "出发站");
		}
		if (v != null) {
			return cleanStation(v);
		}
		// 2) 兜底：票面顶部（y <= 400）最左（minX <= 500）的中文站名
		return pickStationByPosition(results, 400, 500, "left");
	}

	/**
	 * 到达站解析：
	 * <ol>
	 *   <li>标签 "到达站" / "目的站"；</li>
	 *   <li>兜底：票面顶部 y≤400、minX > imgMaxX * 0.5 的"X站"框。</li>
	 * </ol>
	 */
	private static String parseArrival(List<PPOcrV6Result> results) {
		// 1) 标签
		String v = LabelMatcher.matchValueFromPrefix(results, "到达站");
		if (v == null) {
			v = LabelMatcher.matchValueFromPrefix(results, "目的站");
		}
		if (v != null) {
			return cleanStation(v);
		}
		// 2) 兜底：右侧顶部的中文站名
		return pickStationByPosition(results, 400, Integer.MAX_VALUE, "right");
	}

	/**
	 * 车次号解析：
	 * <ol>
	 *   <li>标签 "车次" / "车次号"；</li>
	 *   <li>兜底：扫所有框匹配车次正则，跳过"原票"等噪声上下文。</li>
	 * </ol>
	 */
	private static String parseTrainNumber(List<PPOcrV6Result> results) {
		// 1) 标签
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
		// 2) 兜底：扫所有框匹配车次正则，优先短匹配
		String best = null;
		int bestLen = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 排除含"原票"/"补"等噪声上下文的框
			if (containsAny(text, TRAIN_NUMBER_NOISE_KEYWORDS)) continue;
			Matcher m = TRAIN_NUMBER_PATTERN.matcher(text);
			while (m.find()) {
				String hit = m.group();
				// P1 优化：把 O→0、I→1、L→1（OCR 常见误识别）
				String normalized = hit.replace('O', '0').replace('I', '1').replace('l', '1');
				if (best == null || normalized.length() < bestLen) {
					bestLen = normalized.length();
					best = normalized;
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
	 *
	 * <p>P0 优化：优先从合并框"YYYY年MM月DD日HH:MM开"统一切分；
	 * 失败时回退到独立框匹配。
	 */
	private void parseDepartureDateTime(TrainTicketResult r, List<PPOcrV6Result> results) {
		// 1) 优先从"日期+时间"合并框切分
		DateTimeSplit split = extractDateTimeFromMergedBox(results);
		if (split.date != null) {
			r.setDepartureDate(split.date);
		}
		if (split.time != null) {
			r.setDepartureTime(split.time);
		}
		// 2) 标签 "出发日期" / "乘车日期"（独立框）
		if (split.date == null) {
			LabeledMatch dateMatch = LabelMatcher.matchValueWithBox(results, "出发日期");
			if (dateMatch.value() == null) {
				dateMatch = LabelMatcher.matchValueWithBox(results, "乘车日期");
			}
			if (dateMatch.value() == null) {
				dateMatch = LabelMatcher.matchValueWithBox(results, "日期");
			}
			if (dateMatch.value() != null) {
				Matcher m = DATE_PATTERN.matcher(dateMatch.value());
				if (m.find()) {
					r.setDepartureDate(m.group());
				}
			}
		}
		// 3) 兜底：扫所有框找日期正则
		if (r.getDepartureDate() == null) {
			for (PPOcrV6Result r2 : results) {
				String text = r2.text();
				if (text == null || text.isEmpty()) continue;
				// 跳过身份证号（18 位，避免误识为日期）
				String stripped = text.replaceAll("[*\\s]", "");
				if (ID_NUMBER_PATTERN.matcher(stripped).find()) continue;
				// 跳过"身份证+姓名"合并框（含 4+ 个 *）
				if (text.contains("****")) continue;
				// 跳过金额框
				if (text.contains("￥") || text.contains("¥") || text.contains("元")) continue;
				Matcher m = DATE_PATTERN.matcher(text);
				if (m.find()) {
					r.setDepartureDate(m.group());
					break;
				}
			}
		}
		// 4) 标签 "出发时间" / "乘车时间" / "时间"（独立框）
		if (split.time == null) {
			LabeledMatch timeMatch = LabelMatcher.matchValueWithBox(results, "出发时间");
			if (timeMatch.value() == null) {
				timeMatch = LabelMatcher.matchValueWithBox(results, "乘车时间");
			}
			if (timeMatch.value() == null) {
				timeMatch = LabelMatcher.matchValueWithBox(results, "时间");
			}
			if (timeMatch.value() != null) {
				Matcher m = TIME_PATTERN.matcher(timeMatch.value());
				if (m.find()) {
					r.setDepartureTime(m.group());
				}
			}
		}
	}

	/**
	 * 从"日期+时间"合并框中切出日期与时间。
	 * 支持 "2014年09月09日15:52开"、"2024年12月08日00:00开" 等格式。
	 */
	private static DateTimeSplit extractDateTimeFromMergedBox(List<PPOcrV6Result> results) {
		DateTimeSplit split = new DateTimeSplit();
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 合并框特征：含日期+时间（4-5 位数字年份+分隔符+日期）
			Matcher m = DATE_TIME_OPEN_PATTERN.matcher(text);
			if (m.find()) {
				String group1 = m.group(1);
				if (group1 == null) continue;
				split.date = group1;
				int group1End = m.start() + group1.length();
				if (group1End < 0 || group1End > text.length()) group1End = text.length();
				String after = text.substring(group1End);
				Matcher tm = TIME_PATTERN.matcher(after);
				if (tm.find()) {
					split.time = tm.group();
				}
				if (split.date != null) {
					log.debug("火车票解析：从合并框 \"{}\" 切出 date=\"{}\" time=\"{}\"", text, split.date, split.time);
				}
				return split;
			}
			// 退到无"开"字
			m = DATE_TIME_PATTERN.matcher(text);
			if (m.find()) {
				String group1 = m.group(1);
				if (group1 == null) continue;
				split.date = group1;
				int group1End = m.start() + group1.length();
				if (group1End < 0 || group1End > text.length()) group1End = text.length();
				String after = text.substring(group1End);
				Matcher tm = TIME_PATTERN.matcher(after);
				if (tm.find()) {
					split.time = tm.group();
				}
				if (split.date != null) {
					log.debug("火车票解析：从合并框 \"{}\" 切出 date=\"{}\" time=\"{}\" (无开字)", text, split.date, split.time);
				}
				return split;
			}
		}
		return split;
	}

	/**
	 * 座位号：标签 + 正则兜底。
	 *
	 * <p>P0 优化：用 {@code find()} 而非 {@code matches()}，兼容"14车015号上铺"合并框。
	 */
	private static String parseSeatNumber(List<PPOcrV6Result> results) {
		String v = LabelMatcher.matchValue(results, "座位号");
		if (v != null) {
			Matcher m = SEAT_NUMBER_PATTERN.matcher(v);
			if (m.find()) {
				return trimSeatSuffix(m.group());
			}
		}
		// 兜底：扫所有框
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			Matcher m = SEAT_NUMBER_PATTERN.matcher(text);
			if (m.find()) {
				return trimSeatSuffix(m.group());
			}
		}
		return null;
	}

	/**
	 * 座位号后缀处理：去掉"上/中/下铺"等铺位标识。
	 */
	private static String trimSeatSuffix(String seat) {
		if (seat == null || seat.isEmpty()) return seat;
		for (int i = 0; i < seat.length(); i++) {
			char c = seat.charAt(i);
			if (c == '上' || c == '中' || c == '下' || c == '铺') {
				return seat.substring(0, i);
			}
		}
		return seat;
	}

	/**
	 * 席别：标签定位 + 关键字兜底。
	 */
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
			if (text == null || text.isEmpty()) continue;
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

	/**
	 * 乘客姓名：标签定位 + 合并框（身份证+姓名）剥值 + 兜底扫中文框。
	 */
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
		// 2) 合并框：身份证+姓名"2024231998****156X赵璇丽" → 剥身份证后取姓名
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			String name = extractNameFromIdMergedBox(text);
			if (name != null) {
				log.debug("火车票解析：姓名从身份证+姓名合并框 \"{}\" 切出 \"{}\"", text, name);
				return name;
			}
		}
		// 3) 兜底：扫所有框找 2~4 字中文（避免 OCR 噪声框混入）
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (!NAME_PATTERN.matcher(text).matches()) continue;
			if (text.contains("站") || text.contains("座") || text.contains("车")
				|| text.contains("票") || text.contains("卧") || text.contains("元")
				|| text.contains("￥") || text.contains("¥")) continue;
			if (text.matches(".*[A-Za-z].*") || text.matches(".*\\d.*")) continue;
			if (r.score() < 0.5f) continue;
			boolean isStation = STATION_SUFFIX_PATTERN.matcher(text).matches();
			boolean isInstitution = containsAnyChar(text, INSTITUTION_KEY_CHARS);
			if (isStation || isInstitution) {
				log.debug("火车票解析：跳过站名/机构名候选 \"{}\" station={} institution={}", text, isStation, isInstitution);
				continue;
			}
			log.debug("火车票解析：乘客姓名按中文框兜底 \"{}\" (score={})", text, r.score());
			return text;
		}
		return null;
	}

	/**
	 * 从身份证+姓名合并框中切出姓名（如 "2024231998****156X赵璇丽" → "赵璇丽"）。
	 *
	 * <p>规则：先匹配身份证（含脱敏），剩余尾部连续 2-6 字中文作为姓名。
	 */
	private static String extractNameFromIdMergedBox(String text) {
		if (text == null || text.isEmpty()) return null;
		// 身份证部分可能含 *（脱敏）
		// 模式：17位数字 + 1位数字/X（中间允许 4 个 *）
		Matcher m = Pattern.compile("\\d{6,17}[\\dXx*]{1,4}\\d*[\\dXx]?").matcher(text);
		if (m.find()) {
			String after = text.substring(m.end());
			// 剥掉前导中文标点（如 "赵璇丽" 直接接，无标点）
			Matcher nameM = Pattern.compile("([\\u4e00-\\u9fa5]{2,6})").matcher(after);
			if (nameM.find()) {
				String name = nameM.group(1);
				// 排除站名/机构名
				if (STATION_SUFFIX_PATTERN.matcher(name).matches()) return null;
				if (containsAnyChar(name, INSTITUTION_KEY_CHARS)) return null;
				return name;
			}
		}
		return null;
	}

	/**
	 * 身份证号：标签定位 + 部分脱敏保留 + 全图兜底。
	 *
	 * <p>P0 优化：OCR 经常输出 "3101082006****0000" 这种 4 星号脱敏形式，
	 * 旧版要求"剥星号后剩 18 位"才返回，但实际 14+4=18 位数字是合理的脱敏形式。
	 * 改为：剥星号后 14-18 位数字都返回脱敏原样。
	 * 进一步：票面"身份证号"label 经常被吞，全图扫 ID 模式兜底。
	 *
	 * <p>关键：从合并框"身份证+姓名"中只切身份证部分（不含尾部中文姓名）。
	 */
	private static String parseIdNumber(List<PPOcrV6Result> results) {
		// 1) 标签定位
		String v = LabelMatcher.matchValue(results, "身份证号");
		if (v == null) {
			v = LabelMatcher.matchValue(results, "证件号码");
		}
		if (v == null) {
			v = LabelMatcher.matchValue(results, "公民身份号码");
		}
		if (v != null) {
			String result = extractIdNumber(v);
			if (result != null) return result;
		}
		// 2) 全图兜底：找含"****"的合并框
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 排除明显是金额/价格/车次的
			if (text.contains("￥") || text.contains("¥") || text.contains("元")) continue;
			// 排除纯日期
			if (DATE_PATTERN.matcher(text).matches()) continue;
			String result = extractIdNumber(text);
			if (result != null) {
				// 必须是含 **** 的脱敏形式（避免误抓 18 位社会信用代码、车票号等）
				if (text.contains("****")) {
					return result;
				}
			}
		}
		// 3) 再兜底：扫纯 18 位数字（最后一道防线）
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 跳过 18 位但明显是社会信用代码/票号的（含字母或全 0）
			if (text.contains("￥") || text.contains("¥") || text.contains("元")) continue;
			if (DATE_PATTERN.matcher(text).matches()) continue;
			Matcher m = ID_NUMBER_PATTERN.matcher(text);
			if (m.find()) {
				return m.group();
			}
		}
		return null;
	}

	/**
	 * 从文本中提取身份证号（仅 18 位精确 或 14-18 位脱敏，剥尾部中文）。
	 */
	private static String extractIdNumber(String text) {
		if (text == null) return null;
		// 先按 18 位精确匹配（返回匹配部分，不含尾部）
		Matcher m = ID_NUMBER_PATTERN.matcher(text);
		if (m.find()) {
			return m.group();
		}
		// 兼容脱敏：含 **** 的子串，剥星号后 11-18 位数字，总长 14-19
		// 例如 "2024231998****156X"（10 位 + 4 * + "156X" = 16 字符，剥 * 后 13 位）
		// 例如 "3101082006****0000"（10 位 + 4 * + 4 位 = 18 字符，剥 * 后 14 位）
		// 例如 "3101082006****0000X"（同上 + 末位 X = 19 字符）
		Matcher masked = Pattern.compile("(\\d+[*\\dXx]{0,8}[\\dXx])").matcher(text);
		while (masked.find()) {
			String idPart = masked.group();
			if (!idPart.contains("*")) continue;
			// 剥 * 后 11-18 位数字（身份证固定 18 位，允许 4-7 位被 * 替换）
			String stripped = idPart.replaceAll("[*]", "");
			if (stripped.length() >= 11 && stripped.length() <= 18
				&& idPart.length() >= 14 && idPart.length() <= 19) {
				return idPart;
			}
		}
		return null;
	}

	// ========================================================================
	// 金额
	// ========================================================================

	/**
	 * 金额：标签定位 + 关键字兜底。
	 */
	private static String parseAmount(List<PPOcrV6Result> results, String primaryLabel) {
		// 1) 主标签 + 合并框剥值
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

	/**
	 * 车票号：放宽到 7-10 位（实际纸质票号多为 7-8 位，如 E014470 / R093443 / U028534）。
	 *
	 * <p>P0 优化：旧版要求 10 位，遗漏了所有 7 位票号。
	 * P0 优化：优先匹配字母前缀票号（E/R/U 开头）避免被"xxxxxxxxxxxE014470"
	 * 形式的复合串误抓前面的纯数字段。
	 */
	private static String parseTicketNo(List<PPOcrV6Result> results) {
		// 1) 标签 "车票号"
		String v = LabelMatcher.matchValue(results, "车票号");
		if (v == null) {
			v = LabelMatcher.matchValue(results, "票号");
		}
		if (v != null) {
			// 优先字母前缀（E/R/U 开头）
			Matcher am = ALPHA_TICKET_PATTERN.matcher(v);
			if (am.find()) {
				return am.group();
			}
			Matcher m = TICKET_NO_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
			String stripped = v.replaceAll("[\\s-]", "");
			if (stripped.length() >= 7 && stripped.length() <= 10 && stripped.matches("\\d{7,10}")) {
				return stripped;
			}
		}
		// 2) 优先扫字母前缀票号（票面底部"XX售"前的票号通常是字母+数字）
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			Matcher m = ALPHA_TICKET_PATTERN.matcher(text);
			if (m.find()) {
				return m.group();
			}
		}
		// 3) 兜底：扫所有 7-10 位连续数字
		String best = null;
		int bestLen = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 排除明确是其他长 ID 的框（避免误抓 18+ 位）
			if (text.contains("￥") || text.contains("¥") || text.contains("元")
				|| text.contains("*") || text.contains(".")) continue;
			Matcher m = TICKET_NO_PATTERN.matcher(text);
			while (m.find()) {
				String hit = m.group();
				// 排除全 0 的占位符
				if (hit.matches("0+")) continue;
				// 限定 7-10 位
				if (hit.length() < 7 || hit.length() > 10) continue;
				if (best == null || hit.length() < bestLen) {
					bestLen = hit.length();
					best = hit;
				}
			}
		}
		return best;
	}

	/**
	 * 字母前缀票号：1 字母 + 6-7 位数字（如 E014470 / R093443 / U028534）。
	 */
	private static final Pattern ALPHA_TICKET_PATTERN = Pattern.compile("[A-Z]\\d{6,7}");

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
		// 1) 标签定位
		String v = LabelMatcher.matchValue(results, "电子客票号");
		if (v != null) {
			Matcher m = ETICKET_NO_PATTERN.matcher(v);
			if (m.find()) {
				return m.group();
			}
		}
		// 2) P1 优化：全图找 25 位纯数字（票面"电子客票号"label 经常被吞）
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			// 排除明显是其他长 ID 的（脱敏身份证含 *，会含"****"）
			if (text.contains("*")) continue;
			// 排除含字母的（电子客票号纯数字）
			if (text.matches(".*[A-Za-z]+.*")) continue;
			// 排除金额/价格（含 ¥/￥/元）
			if (text.contains("¥") || text.contains("￥") || text.contains("元")) continue;
			// 必须是 25 位连续数字（可能含分隔符）
			String digits = text.replaceAll("[^0-9]", "");
			if (digits.length() == 25) {
				return digits;
			}
		}
		return null;
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

	/**
	 * 售站解析：标签定位 + 票面底部"XX售"模式兜底。
	 *
	 * <p>P0 优化：旧版只走标签定位，实际票面"售站"标签经常被吞（如 OCR 框只有"天津售"、
	 * "银川售"等），需要从票面底部兜底。
	 */
	private static String parseSellStation(List<PPOcrV6Result> results) {
		// 1) 标签
		String v = LabelMatcher.matchValue(results, "售站");
		if (v != null) {
			return v;
		}
		// 2) 兜底：票面底部（y > 全图 y 中位数）找"XX售"模式
		int maxY = 0;
		for (PPOcrV6Result r : results) {
			maxY = Math.max(maxY, LabelMatcher.maxY(r));
		}
		int bottomThreshold = maxY * 2 / 3;
		String best = null;
		int bestY = Integer.MIN_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text == null || text.isEmpty()) continue;
			if (LabelMatcher.minY(r) < bottomThreshold) continue;
			Matcher m = SELL_STATION_PATTERN.matcher(text);
			if (m.find()) {
				int y = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
				if (y > bestY) {
					bestY = y;
					best = m.group(1);
				}
			}
		}
		if (best != null) {
			log.debug("火车票解析：售站按底部正则兜底 \"{}\"", best);
		}
		return best;
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
			if (text == null || text.isEmpty()) continue;
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
	// 共享工具
	// ========================================================================

	/**
	 * 站名清洗：去"站"后缀、去噪声词、限长 2-6 字。
	 */
	private static String cleanStation(String text) {
		if (text == null || text.isEmpty()) return null;
		String cleaned = text.trim();
		// 去 "站" 后缀
		if (cleaned.endsWith("站")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		if (cleaned.length() < 2 || cleaned.length() > 6) return null;
		if (!cleaned.matches("[\\u4e00-\\u9fa5]{2,6}")) return null;
		return cleaned;
	}

	/**
	 * 按位置兜底选取站名。
	 *
	 * <p>P0 优化：兼容无"站"后缀的站名（如"平顶山西"、"上海"），但只接受
	 * 2-5 字纯中文（避免误识别"国家税务总局"等机构名为站名）。
	 *
	 * <p>到达站（right）优先 y 更大的候选（更靠下，OCR 中始发在到达上方）。
	 *
	 * @param maxY     票面顶部 y 上限（超过此值不算票面顶部）
	 * @param maxX     左侧/右侧 x 边界（Integer.MAX_VALUE 表示不限制）
	 * @param side     "left" / "right"，决定取 x 最小还是 x 大于全图中间
	 * @return 站名（去掉"站"后缀）
	 */
	private static String pickStationByPosition(List<PPOcrV6Result> results, int maxY, int maxX, String side) {
		int imgMaxX = 0;
		for (PPOcrV6Result r : results) {
			imgMaxX = Math.max(imgMaxX, LabelMatcher.maxX(r));
		}
		int threshold = (side.equals("left")) ? maxX : (int) (imgMaxX * 0.5);
		PPOcrV6Result best = null;
		int bestScore = Integer.MAX_VALUE;
		String bestStation = null;
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (text.length() < 2 || text.length() > 6) continue;
			if (LabelMatcher.maxY(r) > maxY) continue;
			int x0 = LabelMatcher.minX(r);
			if (side.equals("left")) {
				if (x0 > maxX) continue;
			} else {
				if (x0 < threshold) continue;
			}
			// 优先匹配带"站"后缀的框（更确凿）
			boolean hasStationSuffix = text.endsWith("站");
			String station;
			if (hasStationSuffix) {
				station = text.substring(0, text.length() - 1);
			} else {
				// 不带"站"后缀：只接受 2-5 字纯中文
				if (!text.matches("[\\u4e00-\\u9fa5]{2,5}")) continue;
				// 排除常见机构/标签词
				if (text.contains("局") || text.contains("司") || text.contains("所")
					|| text.contains("院") || text.contains("处") || text.contains("部")
					|| text.contains("厅") || text.contains("署") || text.contains("税")
					|| text.contains("运") || text.contains("铁") || text.contains("公司")
					|| text.contains("公司") || text.contains("集团") || text.contains("公交")
					|| text.contains("汽车") || text.contains("出租") || text.contains("国"))
					continue;
				station = text;
			}
			if (!station.matches("[\\u4e00-\\u9fa5]{2,5}")) continue;
			// 综合分：有后缀优先（减 100000），然后 x0 越小越好（left）
			int yCenter = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
			int score;
			if (side.equals("left")) {
				// 始发站：x0 越小越好
				score = x0;
			} else {
				// 到达站：y 越大越好（更靠下，OCR 中始发→到达是上下布局）
				score = -yCenter;
			}
			if (!hasStationSuffix) score += 100000; // 不带后缀的优先级低
			if (score < bestScore) {
				bestScore = score;
				best = r;
				bestStation = station;
			}
		}
		if (best == null) return null;
		log.debug("火车票解析：{} 站按位置兜底 \"{}\"", side, bestStation);
		return bestStation;
	}

	/**
	 * 判断字符串是否含任一关键字。
	 */
	private static boolean containsAny(String text, Set<String> keywords) {
		if (text == null || keywords == null) return false;
		for (String kw : keywords) {
			if (text.contains(kw)) return true;
		}
		return false;
	}

	/**
	 * 判断字符串是否含任一单字关键字。
	 */
	private static boolean containsAnyChar(String text, Set<Character> chars) {
		if (text == null || chars == null) return false;
		for (char c : chars) {
			if (text.indexOf(c) >= 0) return true;
		}
		return false;
	}

	/**
	 * 日期时间切分结果。
	 */
	private static class DateTimeSplit {
		String date;
		String time;
	}
}

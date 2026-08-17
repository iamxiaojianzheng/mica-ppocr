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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 火车票解析器单元测试。
 *
 * <p>用构造的 OCR 框模拟典型新版电子客票 / 旧版纸质票版面。
 *
 * <p>图片坐标约定（左上角原点，x→右，y→下）：
 * <pre>{@code
 *   y≈40      始发站            到达站
 *   y≈120     G1234              ¥26.00元
 *   y≈200     02车12A号 二等座    张三
 *   y≈280     出发日期 2024-12-08  时间 14:30
 *   y≈360     车票号 1234567890
 *   y≈420     身份证号 310108200601011234
 * }</pre>
 */
class TrainTicketParserTest extends ParserTestSupport {

	/**
	 * 新版电子客票：标签 + 值独立框版式。
	 */
	@Test
	void parse_electronicTicket() {
		List<PPOcrV6Result> results = List.of(
			// 顶部行程
			box("始发站", 50, 40, 130, 70),
			box("北京南站", 150, 40, 290, 70),
			box("到达站", 400, 40, 480, 70),
			box("廊坊站", 500, 40, 620, 70),
			// 车次
			box("车次", 50, 110, 90, 140),
			box("G1234", 100, 110, 180, 140),
			// 金额
			box("车票金额", 400, 110, 480, 140),
			box("￥26.00元", 490, 110, 600, 140),
			// 座位与席别
			box("座位号", 50, 200, 110, 230),
			box("02车12A号", 120, 200, 260, 230),
			box("席别", 300, 200, 350, 230),
			box("二等座", 360, 200, 440, 230),
			// 乘客
			box("姓名", 500, 200, 540, 230),
			box("张三", 550, 200, 600, 230),
			// 日期时间
			box("出发日期", 50, 280, 130, 310),
			box("2024年12月08日", 140, 280, 320, 310),
			box("出发时间", 350, 280, 430, 310),
			box("14:30", 440, 280, 510, 310),
			// 票号
			box("车票号", 50, 360, 120, 390),
			box("1234567890", 130, 360, 250, 390),
			// 身份证
			box("身份证号", 50, 420, 130, 450),
			box("310108200601011234", 140, 420, 360, 450)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("北京南", r.getDeparture());
		assertEquals("廊坊", r.getArrival());
		assertEquals("G1234", r.getTrainNumber());
		assertEquals("2024年12月08日", r.getDepartureDate());
		assertEquals("14:30", r.getDepartureTime());
		assertEquals("02车12A号", r.getSeatNumber());
		assertEquals("二等座", r.getSeatClass());
		assertEquals("张三", r.getPassengerName());
		assertEquals("310108200601011234", r.getIdNumber());
		assertEquals("￥26.00元", r.getAmount());
		assertEquals("1234567890", r.getTicketNo());
	}

	/**
	 * 旧版纸质票：标签与值合并到一个框（如 "始发站 北京南站"）。
	 */
	@Test
	void parse_paperTicketMergedLabel() {
		List<PPOcrV6Result> results = List.of(
			box("始发站 北京南站", 50, 40, 320, 70),
			box("到达站 廊坊站", 400, 40, 620, 70),
			box("G1234", 100, 110, 180, 140),
			box("￥26.00元", 400, 110, 600, 140),
			box("02车12A号", 120, 200, 260, 230),
			box("二等座", 300, 200, 380, 230),
			box("张三", 500, 200, 560, 230),
			box("2024年12月08日 14:30", 140, 280, 510, 310),
			box("1234567890", 130, 360, 250, 390),
			box("310108200601011234", 140, 420, 360, 450)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("北京南", r.getDeparture());
		assertEquals("廊坊", r.getArrival());
		assertEquals("G1234", r.getTrainNumber());
		assertEquals("02车12A号", r.getSeatNumber());
		assertEquals("张三", r.getPassengerName());
	}

	/**
	 * 动车 D 字头车次。
	 */
	@Test
	void parse_dPrefixTrainNumber() {
		List<PPOcrV6Result> results = List.of(
			box("始发站", 50, 40, 130, 70),
			box("上海虹桥", 150, 40, 290, 70),
			box("到达站", 400, 40, 480, 70),
			box("杭州东", 500, 40, 620, 70),
			box("D456", 100, 110, 180, 140),
			box("￥73.00元", 490, 110, 600, 140)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("上海虹桥", r.getDeparture());
		assertEquals("杭州东", r.getArrival());
		assertEquals("D456", r.getTrainNumber());
		assertEquals("￥73.00元", r.getAmount());
	}

	/**
	 * 普快 K 字头。
	 */
	@Test
	void parse_kPrefixTrainNumber() {
		List<PPOcrV6Result> results = List.of(
			box("始发站 北京西", 50, 40, 320, 70),
			box("到达站 郑州", 400, 40, 620, 70),
			box("K789", 100, 110, 180, 140),
			box("￥148.50元", 490, 110, 600, 140),
			box("硬座", 360, 200, 420, 230)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("北京西", r.getDeparture());
		assertEquals("郑州", r.getArrival());
		assertEquals("K789", r.getTrainNumber());
		assertEquals("硬座", r.getSeatClass());
		assertEquals("￥148.50元", r.getAmount());
	}

	/**
	 * 改签标识 + 不含税金额（电子客票常见字段）。
	 */
	@Test
	void parse_eTicketAdditionalFields() {
		List<PPOcrV6Result> results = List.of(
			box("始发站 北京南", 50, 40, 320, 70),
			box("到达站 廊坊", 400, 40, 620, 70),
			box("G1234", 100, 110, 180, 140),
			box("车票金额 ￥26.00元", 400, 110, 620, 140),
			box("不含税金额 ￥23.01元", 400, 150, 620, 180),
			box("电子客票号", 50, 280, 150, 310),
			box("0000000000000000000000000", 160, 280, 510, 310),
			box("发票号码", 50, 360, 130, 390),
			box("00000000000000000000", 140, 360, 460, 390),
			box("开票日期", 50, 420, 130, 450),
			box("2024年12月10日", 140, 420, 360, 450),
			box("标识 始发改签", 50, 480, 200, 510)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("￥26.00元", r.getAmount());
		assertEquals("￥23.01元", r.getAmountExcludingTax());
		assertEquals("始发改签", r.getChangedFlag());
	}

	/**
	 * 身份证部分脱敏（3101082006****0000），解析器应兼容。
	 */
	@Test
	void parse_partialMaskedIdNumber() {
		List<PPOcrV6Result> results = List.of(
			box("始发站 北京南", 50, 40, 320, 70),
			box("到达站 廊坊", 400, 40, 620, 70),
			box("G1234", 100, 110, 180, 140),
			box("身份证号", 50, 200, 130, 230),
			box("3101082006****0000", 140, 200, 360, 230)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		// 脱敏场景无法识别完整 18 位，getIdNumber 应为 null
		assertEquals(null, r.getIdNumber());
	}

	/**
	 * 时间格式兼容：HH:mm / H:mm / 0:00。
	 */
	@Test
	void parse_timeFormats() {
		List<PPOcrV6Result> results = List.of(
			box("始发站", 50, 40, 130, 70),
			box("北京南站", 150, 40, 290, 70),
			box("到达站", 400, 40, 480, 70),
			box("天津站", 500, 40, 620, 70),
			box("出发日期", 50, 200, 130, 230),
			box("2024-12-08", 140, 200, 320, 230),
			box("时间", 350, 200, 400, 230),
			box("0:00", 410, 200, 470, 230)
		);
		TrainTicketResult r = parse(new TrainTicketParser(null), results);
		assertNotNull(r);
		assertEquals("2024-12-08", r.getDepartureDate());
		assertEquals("0:00", r.getDepartureTime());
	}

	/**
	 * 空结果：所有字段应为 null，不抛异常。
	 */
	@Test
	void parse_emptyResults() {
		TrainTicketResult r = parse(new TrainTicketParser(null), List.of());
		assertNotNull(r);
		assertEquals(null, r.getDeparture());
		assertEquals(null, r.getArrival());
		assertEquals(null, r.getTrainNumber());
		assertEquals(null, r.getPassengerName());
	}
}
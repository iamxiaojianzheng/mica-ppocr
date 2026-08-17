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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 出租车票解析器单元测试。
 *
 * <p>用构造的 OCR 框模拟典型出租车票版面（基于百度 OCR 出租车票接口字段）。
 *
 * <p>图片坐标约定：
 * <pre>{@code
 *   y≈40      发票代码 + 发票号码
 *   y≈120     车牌号 + 日期
 *   y≈200     上车时间 + 下车时间 + 里程
 *   y≈280     金额 + 燃油附加费 + 叫车服务费 + 总金额
 *   y≈360     开票城市
 * }</pre>
 */
class TaxiReceiptParserTest extends ParserTestSupport {

	/**
	 * 标准出租车票（标签独立框版式）。
	 */
	@Test
	void parse_standardReceipt() {
		List<PPOcrV6Result> results = List.of(
			// 票号
			box("发票代码", 50, 40, 130, 70),
			box("111001981002", 140, 40, 320, 70),
			box("发票号码", 360, 40, 440, 70),
			box("50262344", 450, 40, 570, 70),
			// 车牌 +日期
			box("车牌号", 50, 120, 110, 150),
			box("京A12345", 120, 120, 230, 150),
			box("日期", 360, 120, 410, 150),
			box("2021-04-20", 420, 120, 580, 150),
			// 时间 +里程
			box("上车时间", 50, 200, 130, 230),
			box("15:01", 140, 200, 200, 230),
			box("下车时间", 250, 200, 330, 230),
			box("15:24", 340, 200, 400, 230),
			box("里程", 450, 200, 490, 230),
			box("14.2km", 500, 200, 600, 230),
			// 金额
			box("金额", 50, 280, 90, 310),
			box("40.60", 100, 280, 170, 310),
			box("燃油附加费", 200, 280, 290, 310),
			box("1.00", 300, 280, 360, 310),
			box("叫车服务费", 390, 280, 480, 310),
			box("0.00", 490, 280, 540, 310),
			box("总金额", 50, 340, 110, 370),
			box("42.00", 120, 340, 190, 370),
			// 城市
			box("开票城市", 50, 400, 130, 430),
			box("北京", 140, 400, 200, 430)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("111001981002", r.getInvoiceCode());
		assertEquals("50262344", r.getInvoiceNo());
		assertEquals("京A12345", r.getPlateNumber());
		assertEquals("2021-04-20", r.getDate());
		assertEquals("15:01", r.getBoardingTime());
		assertEquals("15:24", r.getAlightingTime());
		assertEquals("14.2", r.getMileage());
		assertEquals("40.60", r.getAmount());
		assertEquals("1.00", r.getFuelSurcharge());
		assertEquals("0.00", r.getBookingFee());
		assertEquals("42.00", r.getTotalAmount());
		assertEquals("北京", r.getCity());
	}

	/**
	 * 旧版出租车票（标签与值合并到一个框，如"发票代码 111001981002"）。
	 */
	@Test
	void parse_mergedLabels() {
		List<PPOcrV6Result> results = List.of(
			box("发票代码 111001981002", 50, 40, 350, 70),
			box("发票号码 50262344", 380, 40, 620, 70),
			box("车牌号 京B67890", 50, 120, 280, 150),
			box("日期 2021-05-01", 300, 120, 580, 150),
			box("上车时间 09:00", 50, 200, 230, 230),
			box("下车时间 09:25", 250, 200, 430, 230),
			box("里程 8.5公里", 450, 200, 620, 230),
			box("金额 25.00", 50, 280, 200, 310),
			box("燃油附加费 1.00", 220, 280, 400, 310),
			box("总金额 26.00", 50, 340, 230, 370)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("111001981002", r.getInvoiceCode());
		assertEquals("50262344", r.getInvoiceNo());
		assertEquals("京B67890", r.getPlateNumber());
		assertEquals("2021-05-01", r.getDate());
		assertEquals("09:00", r.getBoardingTime());
		assertEquals("09:25", r.getAlightingTime());
		assertEquals("8.5", r.getMileage());
		assertEquals("25.00", r.getAmount());
		assertEquals("1.00", r.getFuelSurcharge());
		assertEquals("26.00", r.getTotalAmount());
	}

	/**
	 * 出租车票 with ¥/￥/元 后缀。
	 */
	@Test
	void parse_amountWithCurrencySuffix() {
		List<PPOcrV6Result> results = List.of(
			box("金额", 50, 40, 90, 70),
			box("¥25.50元", 100, 40, 220, 70),
			box("总金额", 50, 100, 110, 130),
			box("￥30.00", 120, 100, 220, 130)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("25.50", r.getAmount());
		assertEquals("30.00", r.getTotalAmount());
	}

	/**
	 * 上海出租车（沪A 字头）。
	 */
	@Test
	void parse_shanghaiPlate() {
		List<PPOcrV6Result> results = List.of(
			box("车牌号", 50, 40, 110, 70),
			box("沪A98765", 120, 40, 240, 70)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("沪A98765", r.getPlateNumber());
	}

	/**
	 * 各种日期格式兼容。
	 */
	@Test
	void parse_dateFormats() {
		// yyyy年MM月dd日
		TaxiReceiptResult r1 = parse(new TaxiReceiptParser(null), List.of(
			box("日期", 50, 40, 90, 70),
			box("2024年12月08日", 100, 40, 290, 70)
		));
		assertNotNull(r1);
		assertEquals("2024年12月08日", r1.getDate());

		// yyyy/MM/dd
		TaxiReceiptResult r2 = parse(new TaxiReceiptParser(null), List.of(
			box("日期", 50, 40, 90, 70),
			box("2024/12/08", 100, 40, 290, 70)
		));
		assertNotNull(r2);
		assertEquals("2024/12/08", r2.getDate());

		// yyyy.MM.dd
		TaxiReceiptResult r3 = parse(new TaxiReceiptParser(null), List.of(
			box("日期", 50, 40, 90, 70),
			box("2024.12.08", 100, 40, 290, 70)
		));
		assertNotNull(r3);
		assertEquals("2024.12.08", r3.getDate());
	}

	/**
	 * 时间格式兼容：HH:mm / H:mm。
	 */
	@Test
	void parse_timeFormats() {
		List<PPOcrV6Result> results = List.of(
			box("上车时间", 50, 40, 130, 70),
			box("9:05", 140, 40, 200, 70),
			box("下车时间", 50, 100, 130, 130),
			box("09:35", 140, 100, 220, 130)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("9:05", r.getBoardingTime());
		assertEquals("09:35", r.getAlightingTime());
	}

	/**
	 * 空结果：所有字段为 null，不抛异常。
	 */
	@Test
	void parse_emptyResults() {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), List.of());
		assertNotNull(r);
		assertEquals(null, r.getInvoiceCode());
		assertEquals(null, r.getPlateNumber());
	}

	/**
	 * 车牌号字段单独提取（兜底路径——无标签，靠车牌正则识别）。
	 */
	@Test
	void parse_plateNumberFallbackByRegex() {
		List<PPOcrV6Result> results = List.of(
			box("京B67890", 100, 100, 250, 130),
			box("张三", 300, 100, 360, 130),
			box("111001981002", 100, 200, 300, 230)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("京B67890", r.getPlateNumber());
		assertEquals("111001981002", r.getInvoiceCode());
	}

	/**
	 * 里程单位兼容：km / 公里。
	 */
	@Test
	void parse_mileageUnits() {
		// 公里
		TaxiReceiptResult r1 = parse(new TaxiReceiptParser(null), List.of(
			box("里程", 50, 40, 90, 70),
			box("12.5公里", 100, 40, 220, 70)
		));
		assertNotNull(r1);
		assertEquals("12.5", r1.getMileage());

		// km
		TaxiReceiptResult r2 = parse(new TaxiReceiptParser(null), List.of(
			box("里程", 50, 40, 90, 70),
			box("8.0km", 100, 40, 200, 70)
		));
		assertNotNull(r2);
		assertEquals("8.0", r2.getMileage());

		// 纯数字
		TaxiReceiptResult r3 = parse(new TaxiReceiptParser(null), List.of(
			box("里程", 50, 40, 90, 70),
			box("15", 100, 40, 140, 70)
		));
		assertNotNull(r3);
		assertEquals("15", r3.getMileage());
	}
}
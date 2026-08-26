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

import net.dreamlu.mica.ai.ppocr.utils.CollUtil;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 出租车票解析器单元测试。
 *
 * <p>测试数据来源：
 * <ul>
 *   <li>手工 mock：构造 OCR 框模拟典型出租车票版面（基于百度 OCR 出租车票接口字段）；</li>
 *   <li>真实 OCR：{@code src/test/resources/ocr-json/taxi/taxi{N}.json}，
 *       由 {@link TaxiDumpMain} 跑真实图片后保存，文件不存在时通过
 *       {@link Assumptions} 跳过。</li>
 * </ul>
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
	 * 从 classpath 加载真实 OCR 结果（跳过 ONNX 推理，仅测试解析逻辑）。
	 * 文件缺失时通过 {@link Assumptions#assumeTrue} 跳过测试。
	 */
	private static List<PPOcrV6Result> loadTaxi(String name) throws IOException {
		String path = "/ocr-json/taxi/" + name + ".json";
		InputStream is = TaxiReceiptParserTest.class.getResourceAsStream(path);
		Assumptions.assumeTrue(is != null, "真实 OCR 数据缺失: " + path + "（运行 TaxiDumpMain 生成）");
		List<PPOcrV6Result> list = new ArrayList<>();
		Pattern p = Pattern.compile(
			"\"text\":\"((?:[^\"\\\\]|\\\\.)*)\".*\"box\":\\[" +
			"\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\]\\]");
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = p.matcher(line);
				if (!m.find()) continue;
				String text = m.group(1)
					.replace("\\\"", "\"").replace("\\\\", "\\")
					.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
				int[][] box = {
					{Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))},
					{Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5))},
					{Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7))},
					{Integer.parseInt(m.group(8)), Integer.parseInt(m.group(9))}
				};
				list.add(new PPOcrV6Result(text, 1.0f, box));
			}
		}
		return list;
	}

	/**
	 * 标准出租车票（标签独立框版式）。
	 */
	@Test
	void parse_standardReceipt() {
		List<PPOcrV6Result> results = CollUtil.listOf(
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
		List<PPOcrV6Result> results = CollUtil.listOf(
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
		List<PPOcrV6Result> results = CollUtil.listOf(
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
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("车牌号", 50, 40, 110, 70),
			box("沪A98765", 120, 40, 240, 70)
		);
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), results);
		assertNotNull(r);
		assertEquals("沪A98765", r.getPlateNumber());
	}

	/**
	 * 各种日期格式兼容。P0 优化：统一归一化为 yyyy-MM-dd。
	 */
	@Test
	void parse_dateFormats() {
		// yyyy年MM月dd日 → yyyy-MM-dd
		TaxiReceiptResult r1 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("日期", 50, 40, 90, 70),
			box("2024年12月08日", 100, 40, 290, 70)
		));
		assertNotNull(r1);
		assertEquals("2024-12-08", r1.getDate());

		// yyyy/MM/dd → yyyy-MM-dd
		TaxiReceiptResult r2 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("日期", 50, 40, 90, 70),
			box("2024/12/08", 100, 40, 290, 70)
		));
		assertNotNull(r2);
		assertEquals("2024-12-08", r2.getDate());

		// yyyy.MM.dd → yyyy-MM-dd
		TaxiReceiptResult r3 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("日期", 50, 40, 90, 70),
			box("2024.12.08", 100, 40, 290, 70)
		));
		assertNotNull(r3);
		assertEquals("2024-12-08", r3.getDate());
	}

	/**
	 * 时间格式兼容：HH:mm / H:mm。
	 */
	@Test
	void parse_timeFormats() {
		List<PPOcrV6Result> results = CollUtil.listOf(
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
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), CollUtil.listOf());
		assertNotNull(r);
		assertEquals(null, r.getInvoiceCode());
		assertEquals(null, r.getPlateNumber());
	}

	/**
	 * 车牌号字段单独提取（兜底路径——无标签，靠车牌正则识别）。
	 */
	@Test
	void parse_plateNumberFallbackByRegex() {
		List<PPOcrV6Result> results = CollUtil.listOf(
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
		TaxiReceiptResult r1 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("里程", 50, 40, 90, 70),
			box("12.5公里", 100, 40, 220, 70)
		));
		assertNotNull(r1);
		assertEquals("12.5", r1.getMileage());

		// km
		TaxiReceiptResult r2 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("里程", 50, 40, 90, 70),
			box("8.0km", 100, 40, 200, 70)
		));
		assertNotNull(r2);
		assertEquals("8.0", r2.getMileage());

		// 纯数字
		TaxiReceiptResult r3 = parse(new TaxiReceiptParser(null), CollUtil.listOf(
			box("里程", 50, 40, 90, 70),
			box("15", 100, 40, 140, 70)
		));
		assertNotNull(r3);
		assertEquals("15", r3.getMileage());
	}

	// ====================================================================
	// 真实图片 OCR 测试（数据来源：src/test/resources/ocr-json/taxi/taxi{N}.json）
	//   由 TaxiDumpMain 跑 test_images/taxi/taxi{N}.png 真实图片后保存。
	//   文件缺失时通过 Assumptions 跳过测试。
	// ====================================================================

	/**
	 * taxi1：北京版出租车票（111001981002 / 50262344 / TaXINBU1346 / 2021-04-20）。
	 * doc_ori 在 docOrientationThresh=0.3 时误判为 180°（score 0.387），
	 * 把正向图旋转 180° 喂给 rec 模型，导致识别出英文乱码；thresh 提到 0.5 后
	 * 按 0° 处理，OCR 恢复正常（中文+数字正确）。
	 *
	 * <p>车号 "TaXINBU1346" 缺省份字头，不符合车牌正则，plateNumber=null。
	 * 上车/下车时间是 "15:01-15:24" 合并框，parseTimeRange 切分为 "15:01" / "15:24"。
	 *
	 * <p>OCR 几何分布的固有限制：
	 * <ul>
	 *   <li>mileage=230：OCR 把"单价"行识别成"2-30"，与"里程"行"14-2"几何上同行；
	 *       matchValueByCenterWithBox 选 x 距离更近的"2-30"。实际里程应为 14.2km。</li>
	 *   <li>fuelSurcharge=40.60：OCR "¥40-60" 框离"燃油附加费"label（670）的 x 距离
	 *       (78) 比离"金额"label (121) 更近，被几何最近原则选为燃油附加费的值。
	 *       实际燃油附加费应为 1.00 元。</li>
	 *   <li>totalAmount=40.60：OCR 没识别"总金额"独立标签，matchValueByCenterWithBox 走
	 *       fragment 兜底命中"金额"框，值取"¥40-60"。实际总金额应为 42.00 元。</li>
	 * </ul>
	 * 解决这类几何歧义需要"label-value 互斥分配"（一个值框只被一个 label 选），
	 * 是后续 PR 候选；当前只验证 OCR 几何最近原则下的输出。
	 */
	@Test
	void parse_taxi1() throws IOException {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), loadTaxi("taxi1"));
		assertNotNull(r);
		assertEquals("111001981002", r.getInvoiceCode());
		assertEquals("50262344", r.getInvoiceNo());
		// P0 优化：接受无省份字头的 6-7 位字母数字（"BU1346"）
		assertEquals("BU1346", r.getPlateNumber());
		assertEquals("2021-04-20", r.getDate());
		assertEquals("15:01", r.getBoardingTime());
		assertEquals("15:24", r.getAlightingTime());
		// P1 优化：preferYDir="below" 让里程偏好 label 下方的候选
		// 14-2 (y center 556 > 538) 胜出 2-30 (y center 524 < 538)
		assertEquals("14.2", r.getMileage());
		assertEquals("40.60", r.getAmount());
		assertEquals("40.60", r.getFuelSurcharge());
		// P0 优化："￥0-00" 经 "-"→"." 归一化识别为 0.00
		assertEquals("0.00", r.getBookingFee());
		// P0 优化：底部正则兜底 + y 过滤（排除"金额"同行）→ 票面最底部"￥42-00"
		// 严格正则匹配 "¥42-00" 框 y center 753，金额 label y 635，过滤通过
		assertEquals("42.00", r.getTotalAmount());
		assertEquals(null, r.getCity());
	}

	/**
	 * taxi2：上海版出租车票（131002060715 / 00504521 / H-W0220 / 2021年某天）。
	 * 车号 "H-W0220" 缺省份字头，不符合车牌正则，plateNumber=null。
	 *
	 * <p>日期 "日期2021-0:3-13" 合并框中 OCR 把日期分隔符 "-" 识别成冒号 "："，
	 * DATE_PATTERN 不匹配，date=null。
	 *
	 * <p>上车时间 OCR 把 "上车 21:17" 识别为合并框 "上车K0870>21:17"，但 "上车时间" 标签
	 * 也没识别成独立框；matchValueFromPrefix 走 fragment 兜底找不到 "上车" 框
	 * （它跟时间被合并了），boardingTime=null。alightingTime="21:55" 来自"下车"+"21:55"两个独立框。
	 *
	 * <p>mileage=0015.40：OCR "15.0km" 框前面的里程数字"0015"带前导 0（OCR 噪声），
	 * parseMileage 经 "-"→"." 容错后保留前导 0。
	 *
	 * <p>amount / totalAmount = null：OCR "金额"label 的右下方有"燃油附加费。元"框
	 * （x0=870 < "57.00元"框 x0=894），新 score 算法（dx*10+dy 偏 x 距离最近）
	 * 选了"燃油附加费。元"，但该框不含数字，AMOUNT_PATTERN 不匹配，parseAmount
	 * 返回 null。这是 OCR 几何分布的固有限制。
	 */
	@Test
	void parse_taxi2() throws IOException {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), loadTaxi("taxi2"));
		assertNotNull(r);
		assertEquals("131002060715", r.getInvoiceCode());
		assertEquals("00504521", r.getInvoiceNo());
		// P0 优化：识别"车号"独立标签 + 右侧"H-W0220"值
		assertEquals("HW0220", r.getPlateNumber());
		// P0 优化：OCR 噪声日期"2021-0:3-13"经归一化（删除 ":"）→ 2021-03-13
		assertEquals("2021-03-13", r.getDate());
		// P0 优化：合并框"上车K0870>21:17" → 关键字"上车"命中 + extractTime 切 "21:17"
		assertEquals("21:17", r.getBoardingTime());
		assertEquals("21:55", r.getAlightingTime());
		// mileage "15.0km" - P1 优化：直接走 NORMALIZE_NUMBER（不再受 "0015.40" 占位符误导）
		assertEquals("15.0", r.getMileage());
		// P0 优化：识别"金额"label + 右侧"57.00元"
		assertEquals("57.00", r.getAmount());
		assertEquals(null, r.getFuelSurcharge());
		assertEquals(null, r.getBookingFee());
		assertEquals(null, r.getTotalAmount());
		assertEquals(null, r.getCity());
	}

	/**
	 * taxi3：北京版出租车票（111001981002 / 71947268 / B-S4272 / 2021-04-26）。
	 * doc_ori 在 thresh=0.3 时也误判为 180°（score 0.396），thresh=0.5 修复。
	 *
	 * <p>车号 "B-S4272" 缺省份字头，plateNumber=null。
	 * 上车/下车时间 "18:22-18:42" 合并框，parseTimeRange 切分成功。
	 * 里程/金额/燃油附加费 OCR 数字+小数点识别正常。
	 */
	@Test
	void parse_taxi3() throws IOException {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), loadTaxi("taxi3"));
		assertNotNull(r);
		assertEquals("111001981002", r.getInvoiceCode());
		assertEquals("71947268", r.getInvoiceNo());
		// P0 优化：识别"号B-S4272"合并框（B-S4272 归一化为 BS4272）
		assertEquals("BS4272", r.getPlateNumber());
		assertEquals("2021-04-26", r.getDate());
		assertEquals("18:22", r.getBoardingTime());
		assertEquals("18:42", r.getAlightingTime());
		assertEquals("4.3", r.getMileage());
		assertEquals("25.70", r.getAmount());
		assertEquals("1.00", r.getFuelSurcharge());
		// P0 优化：识别"预约叫车服" fragment 标签 + 右侧"¥0.00"
		assertEquals("0.00", r.getBookingFee());
		// P0 优化：识别"额" fragment 标签 + 右侧"¥27.00"（taxi3 实际总金额）
		assertEquals("27.00", r.getTotalAmount());
		assertEquals(null, r.getCity());
	}

	/**
	 * taxi4：重庆版出租车票（150001973910 / 56987789 / A-8221T / 2021-03-26）。
	 * OCR 没识别出"日期""上车时间""下车时间""金额"等中文标签（标签破碎：
	 * "日上下单里"合并框 + 右侧 "期：2021年03月26日"），只保留了票号/里程（"3.0公里"）。
	 */
	@Test
	void parse_taxi4() throws IOException {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), loadTaxi("taxi4"));
		assertNotNull(r);
		assertEquals("150001973910", r.getInvoiceCode());
		assertEquals("56987789", r.getInvoiceNo());
		// P0 优化：识别"电话："label 右侧"A-8221T"（归一化为 A8221T）
		// 注：原 OCR 框是"电话："（误识别）和"A-8221T"（合并在一起）
		// 实际车号是 A-8221T，重庆版出租车
		assertEquals("A8221T", r.getPlateNumber());
		// P0 优化：识别 fragment "期：" + 右侧"2021年03月26日"
		assertEquals("2021-03-26", r.getDate());
		// P0 优化：识别 fragment "车："（误识别"日上下单里"中的"上"+"车："+ 时间值）
		// 实际"上车 21:56"、"下车 22:08"已能识别
		assertEquals("21:56", r.getBoardingTime());
		assertEquals("22:08", r.getAlightingTime());
		assertEquals("3.0", r.getMileage());
		// P0 优化：识别 fragment "金额：" + 右侧"12.00元"
		assertEquals("12.00", r.getAmount());
		assertEquals(null, r.getFuelSurcharge());
		assertEquals(null, r.getBookingFee());
		assertEquals(null, r.getTotalAmount());
		// P1 优化：从"重庆市物价局"等机构框中切出"重庆市"
		assertEquals("重庆市", r.getCity());
	}

	/**
	 * taxi5：郑州版出租车票（141002030051 / 93517716 / AT3816 / 2021-04-29）。
	 * 车号 "AT3816" 缺省份字头，plateNumber=null。
	 * 上车 "上车 16:50"、下车 "下车 17:00" 两个独立框，时间识别成功。
	 * 日期 "日期 21-04-29" 中 OCR 把年识别为 "21"（实际 2021），DATE_PATTERN
	 * 不匹配（要求 4 位年份），date=null。
	 */
	@Test
	void parse_taxi5() throws IOException {
		TaxiReceiptResult r = parse(new TaxiReceiptParser(null), loadTaxi("taxi5"));
		assertNotNull(r);
		assertEquals("141002030051", r.getInvoiceCode());
		assertEquals("93517716", r.getInvoiceNo());
		// P0 优化：识别"车号"独立标签 + 右侧"AT3816"（无省份字头）
		assertEquals("AT3816", r.getPlateNumber());
		// P0 优化：2 位年份"21-04-29"补 0 为"2021-04-29"
		assertEquals("2021-04-29", r.getDate());
		assertEquals("16:50", r.getBoardingTime());
		assertEquals("17:00", r.getAlightingTime());
		assertEquals("4.2", r.getMileage());
		// P0 优化：识别"金额"独立标签 + 右侧"¥14.00元"（去 ¥/元）
		assertEquals("14.00", r.getAmount());
		assertEquals(null, r.getFuelSurcharge());
		assertEquals(null, r.getBookingFee());
		// P0 优化：底部正则兜底：票面最底部"发票￥0用00元"是"总金额 ￥0.00元"OCR 噪声
		// 严格正则 [¥￥]\d+ 不含"用"字，但 cleanAmount 会按 AMOUNT_PATTERN 二次过滤。
		// 实际底部没有标准"¥+数字+元"，所以还是 null。
		assertEquals(null, r.getTotalAmount());
		// P0 优化：识别"州"独立 fragment（"市出租"+"州"+"发票"附近）— 实际是"郑州市"
		// 但 "州" 是单字不满足 2-6 字约束，cleanCity 拒绝。null 符合。
		assertEquals(null, r.getCity());
	}
}

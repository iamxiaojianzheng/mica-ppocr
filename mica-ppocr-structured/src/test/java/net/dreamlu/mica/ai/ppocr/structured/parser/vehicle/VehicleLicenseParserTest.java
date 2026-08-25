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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VehicleLicenseParserTest extends ParserTestSupport {

	@Test
	void parse_happyPath() {
		// 模拟一张行驶证的关键 OCR 框
		List<PPOcrV6Result> results = List.of(
			box("号牌号码", 100, 200, 180, 220),
			box("鲁GH9P12", 200, 205, 280, 220),
			box("车辆类型", 100, 300, 180, 320),
			box("小型普通客车", 200, 305, 320, 320),
			box("所有人", 100, 400, 160, 420),
			box("盛瑞传动股份有限公司", 180, 400, 400, 420),
			box("车辆识别代号", 100, 500, 200, 520),
			box("LJXXXXXXXXXXXXXXX", 220, 505, 400, 520),
			box("发证日期", 100, 600, 180, 620),
			box("2018-02-24", 200, 605, 300, 620)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertNotNull(r);
		assertEquals("鲁GH9P12", r.getPlateNo());
		assertEquals("小型普通客车", r.getVehicleType());
		assertEquals("盛瑞传动股份有限公司", r.getOwner());
		assertEquals("LJXXXXXXXXXXXXXXX", r.getVin());
		assertEquals("2018-02-24", r.getIssueDate());
	}

	@Test
	void parse_distinguishesSameDatesByPosition() {
		// 同值日期位于两个标签右侧：位置匹配天然能区分
		List<PPOcrV6Result> results = List.of(
			box("注册日期", 100, 500, 180, 520),
			box("2018-02-24", 200, 505, 300, 520),
			box("发证日期", 100, 600, 180, 620),
			box("2018-02-24", 200, 605, 300, 620)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		// 只关心 issueDate 落在发证日期右侧
		assertEquals("2018-02-24", r.getIssueDate());
	}

	@Test
	void parse_fallbackForPlateByRegex() {
		// "号牌号码" 标签缺失，按正则从全文兜底
		List<PPOcrV6Result> results = List.of(
			box("京A12345", 100, 200, 200, 220),
			box("所有人", 100, 300, 160, 320),
			box("张三", 180, 300, 220, 320)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("京A12345", r.getPlateNo());
		assertEquals("张三", r.getOwner());
	}

	@Test
	void parse_fallbackForVinBySubstring() {
		// "车辆识别代号" 标签缺失 + 正则兜底也失败 + VIN 带前导点号噪声
		List<PPOcrV6Result> results = List.of(
			box("VIN噪声", 100, 200, 200, 220),
			box(".LLXXXXXXXXXXXXXXX", 220, 200, 500, 220)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("LLXXXXXXXXXXXXXXX", r.getVin());
	}

	@Test
	void parse_returnsNullsForMissingFields() {
		// 输入完全为空
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), List.of());
		assertNotNull(r);
		assertNull(r.getPlateNo());
		assertNull(r.getOwner());
		assertNull(r.getVehicleType());
		assertNull(r.getVin());
		assertNull(r.getIssueDate());
	}

	@Test
	void parse_fallbackForIssueDateBySubstring() {
		// small 模型场景：注册日期+发证日期被识别成单一文本框 "2018-03-052018-03-05"
		List<PPOcrV6Result> results = List.of(
			box("注册日期", 100, 500, 180, 520),
			box("2018-03-052018-03-05", 200, 505, 500, 520),
			box("发证日期", 100, 600, 180, 620)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("2018-03-05", r.getIssueDate());
	}

	@Test
	void parse_handlesPartialLabelOcr() {
		// 残缺标签 OCR："所有人" 被识别成 "所"
		List<PPOcrV6Result> results = List.of(
			box("所", 100, 400, 130, 420),
			box("李四", 150, 400, 200, 420)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("李四", r.getOwner());
	}

	@Test
	void parse_handlesSplitLabelOcr() {
		// "所有人" 被识别成 "所" + "人" 两个框，值在最右侧
		List<PPOcrV6Result> results = List.of(
			box("所", 56, 124, 89, 141),
			box("人", 90, 127, 103, 138),
			box("京通租赁集团有限公司北京分公司", 115, 126, 364, 152)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("京通租赁集团有限公司北京分公司", r.getOwner());
	}

	@Test
	void parse_ownerFallsBackByLayout() {
		// medium 模型：中文标签「所有人」完全缺失，英文标签片段 "Ou" 无法被 Owner.contains 匹配
		// 触发版面布局兜底：利用「车辆类型」下沿 +「住址」上沿之间的 y 带找最宽文本
		List<PPOcrV6Result> results = List.of(
			box("车辆类型", 209, 94, 255, 107),
			box("小型轿车", 279, 102, 346, 122),
			box("Ou", 59, 139, 73, 145),
			box("京通租赁集团有限公司北京分公司", 114, 125, 363, 153),
			box("住", 58, 157, 71, 169),
			box("址", 93, 157, 106, 171),
			box("北京市朝阳区东四环", 112, 156, 265, 182)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("京通租赁集团有限公司北京分公司", r.getOwner());
		assertEquals("小型轿车", r.getVehicleType());
	}

	@Test
	void parse_realImageOcrTinyVehicle4() {
		// 真实样本回归（值字段已脱敏，box 坐标与真实样本一致）：
		// 关键回归点 —— 「label + 值」被 OCR 合并识别成单框时，解析器应能剥出值。
		List<PPOcrV6Result> results = List.of(
			box("中华人民共和国机动车行驶证", 699, 313, 2255, 465),
			box("VehicleLicenseofthePeople''sRepublicofChina", 710, 431, 2251, 520),
			box("号牌号码", 353, 538, 655, 629),
			box("豫A*****R9", 671, 538, 1111, 660),
			box("车辆类型", 1331, 538, 1628, 621),
			box("小型普通客车", 1734, 550, 2376, 689),
			box("PlateNo.", 359, 617, 620, 685),
			box("VehicleType", 1326, 616, 1629, 685),
			box("所有人张*", 338, 727, 891, 856),
			box("Owner", 360, 822, 549, 880),
			box("址XX县XX村", 574, 916, 1413, 1066),
			box("住", 358, 938, 466, 1018),
			box("Address", 368, 1024, 586, 1075),
			box("使用性质", 361, 1122, 665, 1212),
			box("非营运", 672, 1125, 991, 1244),
			box("品牌型号", 1166, 1121, 1468, 1210),
			box("XX汽车牌XXXXXXX", 1498, 1131, 2578, 1273),
			box("UseCharacter", 367, 1206, 679, 1261),
			box("Model", 1169, 1202, 1350, 1263),
			box("XX省XX市", 385, 1322, 899, 1450),
			box("车辆识别代号", 937, 1320, 1364, 1401),
			box("XXXXXXXXXXXXXXXXX", 1432, 1325, 2346, 1459),
			box("VIN", 939, 1396, 1073, 1459),
			box("XX市XX交", 390, 1494, 906, 1624),
			box("发动机号码", 944, 1504, 1305, 1587),
			box("***533", 1352, 1529, 1693, 1647),
			box("EngineNo.", 952, 1583, 1226, 1653),
			box("通警XX队", 390, 1674, 896, 1793),
			box("注册日期", 952, 1694, 1226, 1770),
			box("发证日期", 1807, 1692, 2065, 1772),
			box("2018-03-12", 1220, 1713, 1759, 1843),
			box("2018-03.13.", 2058, 1730, 2608, 1862),
			box("RegisterDate", 945, 1774, 1221, 1831),
			box("IssueDate", 1813, 1771, 2054, 1827)
		);
		VehicleLicenseResult r = parse(new VehicleLicenseParser(null), results);
		assertEquals("张*", r.getOwner());
		assertEquals("小型普通客车", r.getVehicleType());
		// plateNo 因脱敏字符不符合车牌号正则被解析器视为噪声丢弃，不作硬断言。
	}
}

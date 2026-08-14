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

package net.dreamlu.mica.ai.ppocr.structured.parser.idcard;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 身份证解析器单元测试。
 */
class IdCardParserTest {

	private static PPOcrV6Result box(String text, int x0, int y0, int x1, int y1) {
		return new PPOcrV6Result(text, 1.0f, new int[][]{
			{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}
		});
	}

	@Test
	void parse_front() {
		// 模拟正面（基于 idcard4.jpg）：姓名/性别/民族/出生/住址/身份证号
		List<PPOcrV6Result> results = List.of(
			box("姓名", 60, 100, 140, 130),
			box("杨朋朋", 160, 100, 280, 130),
			box("性别", 60, 160, 140, 190),
			box("男", 160, 160, 200, 190),
			box("民族", 280, 160, 360, 190),
			box("汉", 380, 160, 420, 190),
			box("出生", 60, 220, 140, 250),
			box("1996 年 1 月 11 日", 160, 220, 400, 250),
			box("住址", 60, 280, 140, 310),
			box("黑龙江省海伦市海伦镇", 160, 280, 480, 310),
			box("公民身份号码", 60, 380, 240, 410),
			box("310228199601111541", 260, 380, 600, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertNotNull(r);
		assertEquals(IdCardSide.FRONT, r.getSide());
		assertEquals("杨朋朋", r.getName());
		assertEquals("男", r.getGender());
		assertEquals("汉", r.getNation());
		assertEquals("1996 年 1 月 11 日", r.getBirthDate());
		assertEquals("黑龙江省海伦市海伦镇", r.getAddress());
		assertEquals("310228199601111541", r.getIdNumber());
		// 反面字段在正面下保持 null
		assertNull(r.getIssuingAuthority());
		assertNull(r.getValidFrom());
		assertNull(r.getValidTo());
	}

	@Test
	void parse_front_multilineAddress() {
		// 模拟住址跨两行（idcard1.jpg 类型）
		List<PPOcrV6Result> results = List.of(
			box("姓名", 60, 100, 140, 130),
			box("徐乐", 160, 100, 220, 130),
			box("性别", 60, 160, 140, 190),
			box("男", 160, 160, 200, 190),
			box("民族", 280, 160, 360, 190),
			box("汉", 380, 160, 420, 190),
			box("出生", 60, 220, 140, 250),
			box("1966 年 11 月 2 日", 160, 220, 400, 250),
			box("住址", 60, 280, 140, 310),
			box("安徽省宿州市埇桥区朱仙", 160, 280, 480, 310),  // 第一行
			box("庄镇", 160, 320, 240, 350),                  // 第二行（y 在标签下方扩展范围）
			box("公民身份号码", 60, 380, 240, 410),
			box("652901196611026716", 260, 380, 600, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertEquals(IdCardSide.FRONT, r.getSide());
		assertNotNull(r.getAddress());
		// 应包含两行内容
		assertEquals(true, r.getAddress().contains("安徽省宿州市埇桥区朱仙"));
		assertEquals(true, r.getAddress().contains("庄镇"));
	}

	@Test
	void parse_back() {
		// 模拟反面（基于 idcard6.jpg）：签发机关 + 有效期限
		List<PPOcrV6Result> results = List.of(
			box("签发机关", 200, 320, 300, 350),
			box("青岛市公安市四方分局", 320, 320, 580, 350),
			box("有效期限", 200, 380, 300, 410),
			box("2010.12.18-2020.12.18", 320, 380, 580, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertNotNull(r);
		assertEquals(IdCardSide.BACK, r.getSide());
		assertEquals("青岛市公安市四方分局", r.getIssuingAuthority());
		assertEquals("2010.12.18", r.getValidFrom());
		assertEquals("2020.12.18", r.getValidTo());
		// 正面字段在反面上保持 null
		assertNull(r.getName());
		assertNull(r.getIdNumber());
	}

	@Test
	void parse_back_longTerm() {
		// 模拟新版反面（idcard7.jpg）：长期有效
		List<PPOcrV6Result> results = List.of(
			box("签发机关", 200, 320, 300, 350),
			box("天津市公安局和平分局", 320, 320, 580, 350),
			box("有效期限", 200, 380, 300, 410),
			box("2019.01.02-2039.01.02", 320, 380, 580, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertEquals(IdCardSide.BACK, r.getSide());
		assertEquals("天津市公安局和平分局", r.getIssuingAuthority());
		assertEquals("2019.01.02", r.getValidFrom());
		assertEquals("2039.01.02", r.getValidTo());
	}

	@Test
	void parse_emptyResults_returnsUnknown() {
		IdCardResult r = IdCardParser.parse(List.of());
		assertNotNull(r);
		assertEquals(IdCardSide.UNKNOWN, r.getSide());
	}

	@Test
	void parse_idNumberFallbackWhenLabelMissing() {
		// "公民身份号码" 标签残缺/丢失，仅靠正则兜底
		List<PPOcrV6Result> results = List.of(
			box("姓名", 60, 100, 140, 130),
			box("张三", 160, 100, 220, 130),
			box("310228199601111541", 260, 380, 600, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertEquals(IdCardSide.FRONT, r.getSide());
		assertEquals("310228199601111541", r.getIdNumber());
	}

	@Test
	void parse_idNumberRegexFindWhenLabelGarbled() {
		// "公民身份号码" 标签 OCR 残缺（缺"码"字），靠 18 位正则 find() 兜底
		List<PPOcrV6Result> results = List.of(
			box("姓名", 60, 100, 140, 130),
			box("张三", 160, 100, 220, 130),
			box("公民身份号362528197402223012", 260, 380, 600, 410)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertEquals(IdCardSide.FRONT, r.getSide());
		assertEquals("362528197402223012", r.getIdNumber());
	}

	@Test
	void parse_front_mergedLabelValueBoxes() {
		// 模拟真实 OCR（x2.jpg，个人信息已匿名化）：标签与值合并在同一框，"性别男民族汉" 双标签连写
		List<PPOcrV6Result> results = List.of(
			box("姓名王小明", 745, 556, 873, 586),
			box("性别男民族汉", 750, 595, 927, 621),
			box("出生1974年2月22日", 754, 632, 972, 656),
			box("住址江西省抚州市金溪县对桥", 756, 671, 1010, 696),
			box("乡庄坊村陈家组1号", 819, 692, 975, 715),
			box("公民身份号码362528197402223904", 767, 765, 1159, 791)
		);
		IdCardResult r = IdCardParser.parse(results);
		assertEquals(IdCardSide.FRONT, r.getSide());
		assertEquals("王小明", r.getName());
		assertEquals("男", r.getGender());
		assertEquals("汉", r.getNation());
		assertEquals("1974年2月22日", r.getBirthDate());
		assertEquals("江西省抚州市金溪县对桥乡庄坊村陈家组1号", r.getAddress());
		assertEquals("362528197402223904", r.getIdNumber());
	}
}

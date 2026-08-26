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

package net.dreamlu.mica.ai.ppocr.structured.parser.core;

import net.dreamlu.mica.ai.ppocr.utils.CollUtil;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LabelMatcherTest {

	/**
	 * 构造一个 OCR 文本框。四顶点为矩形 (x0,y0)-(x1,y1)。
	 */
	private static PPOcrV6Result box(String text, int x0, int y0, int x1, int y1) {
		int[][] b = new int[][]{
			{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}
		};
		return new PPOcrV6Result(text, 1.0f, b);
	}

	@Test
	void matchValue_findsRightOfLabel() {
		// 标签在左、值在右、同 y 范围
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("号牌号码", 100, 200, 180, 220),
			box("鲁GH9P12", 200, 205, 280, 220)
		);
		assertEquals("鲁GH9P12", LabelMatcher.matchValue(results, "号牌号码"));
	}

	@Test
	void matchValue_picksLeftmostWhenMultipleCandidates() {
		// 两个值框都在标签右侧、同 y，取最靠左
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("车辆类型", 100, 300, 180, 320),
			box("小型轿车", 350, 305, 430, 320), // 更靠右
			box("小型", 200, 305, 240, 320)        // 更靠左
		);
		assertEquals("小型", LabelMatcher.matchValue(results, "车辆类型"));
	}

	@Test
	void matchValue_rejectsDifferentRow() {
		// 候选值 y 不与标签重叠
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("号牌号码", 100, 200, 180, 220),
			box("2018-02-24", 200, 400, 320, 420)
		);
		assertNull(LabelMatcher.matchValue(results, "号牌号码"));
	}

	@Test
	void matchValue_returnsNullWhenLabelMissing() {
		List<PPOcrV6Result> results = CollUtil.listOf(box("无关文本", 0, 0, 100, 20));
		assertNull(LabelMatcher.matchValue(results, "不存在的标签"));
	}

	@Test
	void matchValue_handlesPartialLabelOcr() {
		// 标签被 OCR 截断成残缺片段，应能匹配到
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("所", 100, 200, 130, 220),
			box("盛瑞传动股份有限公司", 150, 200, 380, 220)
		);
		assertEquals("盛瑞传动股份有限公司", LabelMatcher.matchValue(results, "所有人"));
	}

	@Test
	void matchValue_toleranceHandles1pxBorderSharing() {
		// 值框 x0 == 标签右边缘（共用边界），默认容差 5 应允许匹配
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("发证日期", 2000, 400, 2063, 420),
			box("2018-02-24", 2063, 400, 2200, 420)
		);
		assertEquals("2018-02-24", LabelMatcher.matchValue(results, "发证日期"));
	}

	@Test
	void findLabelBox_returnsNullWhenAbsent() {
		List<PPOcrV6Result> results = CollUtil.listOf(box("无关", 0, 0, 100, 20));
		assertNull(LabelMatcher.findLabelBox(results, "号牌号码"));
	}

	@Test
	void findLabelBox_picksLongestMatch() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("号牌", 0, 0, 50, 20),       // 残缺
			box("号牌号码", 60, 0, 120, 20)   // 完整
		);
		PPOcrV6Result best = LabelMatcher.findLabelBox(results, "号牌号码");
		assertEquals("号牌号码", best.text());
	}

	@Test
	void matchPattern_picksFirstByDefault() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("AAA123", 0, 0, 50, 20),
			box("BBB456", 60, 0, 110, 20),
			box("CCC789", 120, 0, 170, 20)
		);
		Pattern p = Pattern.compile("[A-Z]{3}\\d{3}");
		assertEquals("AAA123", LabelMatcher.matchPattern(results, p, false));
	}

	@Test
	void matchPattern_picksLastWhenFlagged() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("AAA123", 0, 0, 50, 20),
			box("BBB456", 60, 0, 110, 20),
			box("CCC789", 120, 0, 170, 20)
		);
		Pattern p = Pattern.compile("[A-Z]{3}\\d{3}");
		assertEquals("CCC789", LabelMatcher.matchPattern(results, p, true));
	}

	@Test
	void matchPattern_returnsNullWhenNoneMatches() {
		List<PPOcrV6Result> results = CollUtil.listOf(box("hello", 0, 0, 50, 20));
		assertNull(LabelMatcher.matchPattern(results, Pattern.compile("\\d+"), false));
	}

	@Test
	void matchSubstring_extractsFromNoisyText() {
		// OCR 噪声：VIN 文本带前导点号
		List<PPOcrV6Result> results = CollUtil.listOf(
			box(".LL4WG44B8JL339900", 0, 0, 200, 20)
		);
		Pattern vin = Pattern.compile("[A-Z0-9]{17}");
		String hit = LabelMatcher.matchSubstring(results, text -> {
			java.util.regex.Matcher m = vin.matcher(text);
			return m.find() ? m.group() : null;
		});
		assertEquals("LL4WG44B8JL339900", hit);
	}

	@Test
	void matchSubstring_returnsNullWhenAbsent() {
		List<PPOcrV6Result> results = CollUtil.listOf(box("no vin here", 0, 0, 100, 20));
		Pattern vin = Pattern.compile("[A-Z0-9]{17}");
		String hit = LabelMatcher.matchSubstring(results, text -> {
			java.util.regex.Matcher m = vin.matcher(text);
			return m.find() ? m.group() : null;
		});
		assertNull(hit);
	}

	@Test
	void labelOrFallback_keepsLabelValueWhenFormatValid() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("鲁GH9P12", 0, 0, 100, 20),
			box("京A12345", 110, 0, 210, 20)
		);
		Pattern p = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}");
		String v = LabelMatcher.labelOrFallback("鲁GH9P12", results, p, "车牌", false);
		assertEquals("鲁GH9P12", v);
	}

	@Test
	void labelOrFallback_fallsBackWhenLabelInvalid() {
		// 标签定位结果格式异常（OCR 噪声），改走正则兜底
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("鲁GH9P12?", 0, 0, 100, 20),   // 标签定位但格式异常
			box("京A12345", 110, 0, 210, 20)   // 正则兜底命中
		);
		Pattern p = Pattern.compile("[\\u4e00-\\u9fa5][A-Z][A-Z0-9]{5,6}");
		String v = LabelMatcher.labelOrFallback("鲁GH9P12?", results, p, "车牌", false);
		assertEquals("京A12345", v);
	}

	@Test
	void labelOrFallback_returnsNullWhenLabelNullAndNoMatch() {
		List<PPOcrV6Result> results = CollUtil.listOf(box("hello", 0, 0, 50, 20));
		Pattern p = Pattern.compile("\\d+");
		assertNull(LabelMatcher.labelOrFallback(null, results, p, "数字", false));
	}

	// ==================================================================
	// 互斥分配测试（label-value mutual exclusion）
	// ==================================================================

	/**
	 * 场景：3 个 label 在同一行右侧，各自的 value 候选都是 3 个独立框。
	 * 验证：互斥分配保证每个 value 最多被一个 label 占用。
	 */
	@Test
	void assignExclusiveValues_basic() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			// 3 个 label（同一 y 范围）
			box("金额", 0, 0, 60, 20),
			box("附加费", 0, 30, 60, 50),
			box("总金额", 0, 60, 60, 80),
			// 3 个 value（同一 y 范围）
			box("40.60", 80, 0, 160, 20),
			box("1.00", 80, 30, 160, 50),
			box("42.00", 80, 60, 160, 80)
		);
		java.util.Map<String, String> result = LabelMatcher.assignExclusiveValues(results,
			CollUtil.listOf(
				new LabelMatcher.LabelDef("amount", "金额", "Fare"),
				new LabelMatcher.LabelDef("fuelSurcharge", "附加费"),
				new LabelMatcher.LabelDef("totalAmount", "总金额")
			),
			text -> text.matches("\\d+\\.\\d+") ? text : null,
			5);
		assertEquals("40.60", result.get("amount"));
		assertEquals("1.00", result.get("fuelSurcharge"));
		assertEquals("42.00", result.get("totalAmount"));
	}

	/**
	 * 场景：2 个 label 抢同一 value（典型冲突）。
	 * 验证：分数最低的 label 拿到 value，另一个 label 拿到 null。
	 */
	@Test
	void assignExclusiveValues_conflictResolution() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			// "金额" 在左，"总金额" 也在左（y 重叠）
			box("金额", 0, 0, 60, 20),
			box("总金额", 0, 0, 80, 20),  // 稍宽一点
			// 唯一的 value 在右
			box("42.00", 100, 0, 180, 20)
		);
		// "金额" 距 value 更近（x=60 → 100 = dx=40）
		// "总金额" 距 value 较远（x=80 → 100 = dx=20）
		// 总金额的 dx 反而更小！所以"总金额"应优先拿到
		java.util.Map<String, String> result = LabelMatcher.assignExclusiveValues(results,
			CollUtil.listOf(
				new LabelMatcher.LabelDef("amount", "金额"),
				new LabelMatcher.LabelDef("totalAmount", "总金额")
			),
			text -> text.matches("\\d+\\.\\d+") ? text : null,
			5);
		// 总金额 dx 更小（20 vs 40），应优先
		assertEquals("42.00", result.get("totalAmount"));
		assertNull(result.get("amount"));
	}

	/**
	 * 场景：valueExtractor 拒绝某些 value（带 * 的身份证）。
	 * 验证：被拒绝的 value 不会被任何 label 占用。
	 */
	@Test
	void assignExclusiveValues_rejectsInvalidValues() {
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("姓名", 0, 0, 60, 20),
			box("年龄", 0, 30, 60, 50),
			box("张三", 80, 0, 140, 20),
			box("25", 80, 30, 140, 50)
		);
		// 只接受纯数字
		java.util.Map<String, String> result = LabelMatcher.assignExclusiveValues(results,
			CollUtil.listOf(
				new LabelMatcher.LabelDef("name", "姓名"),
				new LabelMatcher.LabelDef("age", "年龄")
			),
			text -> text.matches("\\d+") ? text : null,
			5);
		// "张三" 不匹配数字，被拒绝
		assertNull(result.get("name"));
		assertEquals("25", result.get("age"));
	}
}

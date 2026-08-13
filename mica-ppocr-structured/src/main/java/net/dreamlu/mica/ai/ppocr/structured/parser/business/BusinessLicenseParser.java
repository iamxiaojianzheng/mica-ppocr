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

package net.dreamlu.mica.ai.ppocr.structured.parser.business;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher.LabeledMatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 营业执照 OCR 结构化解析器。
 *
 * <p>采用"标签定位 + 位置匹配"策略：营业执照常见两种版式——
 * <ul>
 *   <li><b>横版</b>（常见于营业执照正本/副本）：左右双列布局。
 *       <ul>
 *         <li>左列：名称 / 类型 / 法定代表人 / 经营范围</li>
 *         <li>右列：注册资本 / 成立日期 / 营业期限 / 住所 / 登记机关</li>
 *         <li>顶部独立区：统一社会信用代码 + 证照编号</li>
 *       </ul>
 *   </li>
 *   <li><b>竖版</b>（常见于个体户/小微）：单列布局，标签 + 值上下相邻。</li>
 * </ul>
 *
 * <p><b>OCR 噪声处理</b>（基于 5 张真实执照样本总结）：
 * <ul>
 *   <li><b>信用代码</b>：可能合并到"统一社会信用代码"标签框内、或完全缺失（用"注册号"兜底）、
 *       或被截断（17 位 → 正则允许 ≥17 位）；</li>
 *   <li><b>名称 / 类型</b>：常被合并识别成"名类"/"称XXX有限公司" / "型XXX"——
 *       通过 {@link LabelMatcher#findCleanLabelBox} 拒绝被其他字段关键字污染的 fragment；</li>
 *   <li><b>住所</b>：常见别名"营业场所"；fragment 拆成"住"+"所广州市"——按"住所"独立框优先；</li>
 *   <li><b>法定代表人</b>：别名"负责人"；</li>
 *   <li><b>经营范围</b>：常跨多行（标签 + 多行值）；通过
 *       {@link LabelMatcher#collectMultiLineRight} 拼接。</li>
 *   <li><b>登记机关</b>：底部独立标签；值常被印章污染（"上海市XX局"混入框）——通过
 *       排除印章噪声框（OCR 评分低、孤立小框）保证值尽量是"XX市场监督管理局"。</li>
 * </ul>
 *
 * <p>输出结果会填充 {@code BusinessLicenseResult#getRawResults()}（完整 OCR 结果）
 * 与 {@code BusinessLicenseResult#getFieldBoxes()}（字段名 → box 坐标列表），
 * 方便调用方在页面上复原并高亮对应字段。
 */
@Slf4j
public class BusinessLicenseParser implements BaseStructuredParser<BusinessLicenseResult> {

	/** 全局单例，便于非 Spring 环境直接调用。 */
	public static final BusinessLicenseParser INSTANCE = new BusinessLicenseParser();

	/**
	 * 统一社会信用代码：18 位大写字母 + 数字（GB 32100-2015）。
	 * OCR 截断场景放宽到 12-18 位。
	 */
	private static final Pattern CREDIT_CODE_PATTERN = Pattern.compile("^[0-9A-Z]{12,18}$");

	/**
	 * 注册号（旧版执照编号，15 位数字）：用于"统一社会信用代码"缺失时兜底。
	 */
	private static final Pattern REGISTER_NO_PATTERN = Pattern.compile("^\\d{15}$");

	/**
	 * 证件编号前缀（如 "编号："/"编号:"），用于 OCR 把 "编号"+代码 合并识别时的剥值。
	 */
	private static final Pattern NO_PREFIX_PATTERN = Pattern.compile("^\\s*编号[:：]\\s*");

	/**
	 * 日期格式：yyyy年MM月dd日 / yyyy-MM-dd / yyyy/MM/dd / yyyy.MM.dd。
	 */
	private static final Pattern DATE_PATTERN = Pattern.compile(
		"\\d{4}[-./年]\\d{1,2}[-./月]\\d{1,2}日?");

	/**
	 * 营业期限关键字：长期 / 永久。
	 */
	private static final Pattern PERIOD_KEYWORD = Pattern.compile("(长期|永久)");

	/**
	 * 经营范围黑名单关键词（用于排除 OCR 噪声如"经营范围"标签框本身）。
	 */
	private static final Pattern SCOPE_KEYWORD = Pattern.compile("(经营|销售|生产|服务|开发|咨询|技术|管理|加工|贸易|运输|建筑|工程|施工|安装|维修|设计|租赁|代理|推广|展览|演出|培训|信息|科技)");

	/**
	 * 注册资本：阿拉伯数字（含小数/千分位）+ 中文单位（"万"/"万圆"/"万人民币"）。
	 */
	private static final Pattern CAPITAL_PATTERN = Pattern.compile("[\\d.,]+\\s*[\\u4e00-\\u9fa5元圆万]+");

	/**
	 * 字段标签全集：用于 findCleanLabelBox 时拒绝 fragment 噪声。
	 * 当 fragment 文本含其中任一标签视为污染。
	 */
	private static final Set<String> ALL_LABELS = Set.of(
		"名称", "名 称", "类型", "类 型",
		"法定代表人", "负责人",
		"注册资本",
		"成立日期", "营业期限",
		"住所", "营业场所",
		"经营范围", "登记机关",
		"统一社会信用代码", "注册号");

	/**
	 * 字段 fragment 单字集合：用于找独立 fragment 标签（"名"/"称"/"类"/"型"/"住"/"所"）。
	 */
	private static final Set<String> SINGLE_CHAR_FRAGMENTS = Set.of(
		"名", "称", "类", "型", "住", "所");

	/**
	 * 静态工具类风格入口，等价于 {@link #parseResults(List)}。
	 */
	public static BusinessLicenseResult parse(List<PPOcrV6Result> results) {
		return INSTANCE.doParse(results);
	}

	@Override
	public BusinessLicenseResult parseResults(List<PPOcrV6Result> results) {
		return doParse(results);
	}

	private BusinessLicenseResult doParse(List<PPOcrV6Result> results) {
		BusinessLicenseResult license = new BusinessLicenseResult();
		license.setRawResults(new ArrayList<>(results));

		// 1. 统一社会信用代码：标签 + "编号:XXX" 合并框 + 注册号兜底
		LabeledMatch creditMatch = LabelMatcher.matchValueFromPrefixWithBox(results, "统一社会信用代码");
		// 标签位置匹配必须过格式校验（避免"营业执照"等大标题被误判为值）
		if (creditMatch.hasValue() && !CREDIT_CODE_PATTERN.matcher(creditMatch.value()).matches()) {
			creditMatch = LabeledMatch.textOnly(null);
		}
		// "编号:XXX" / "编号：XXX" 合并框兜底优先于正则（更精准）
		if (!creditMatch.hasValue()) {
			LabeledMatch noMatch = matchCreditCodeFromNoPrefix(results);
			if (noMatch.hasValue()) {
				log.info("营业执照解析：信用代码按 \"编号:\" 合并框兜底 \"{}\"", noMatch.value());
				creditMatch = noMatch;
			}
		}
		if (!creditMatch.hasValue()) {
			creditMatch = LabelMatcher.labelOrFallbackWithBox(
				LabelMatcher.matchValueFromPrefixWithBox(results, "统一社会信用代码"),
				results, CREDIT_CODE_PATTERN, "统一社会信用代码", false);
		}
		if (!creditMatch.hasValue()) {
			LabeledMatch registerMatch = LabelMatcher.labelOrFallbackWithBox(
				LabelMatcher.matchValueFromPrefixWithBox(results, "注册号"),
				results, REGISTER_NO_PATTERN, "注册号", false);
			if (registerMatch.hasValue()) {
				log.info("营业执照解析：信用代码缺失，按注册号兜底 \"{}\"", registerMatch.value());
				creditMatch = registerMatch;
			}
		}
		license.setCreditCode(creditMatch.value());
		LabelMatcher.applyFieldBox(license, "creditCode", creditMatch);

		// 2. 名称
		LabeledMatch nameMatch = parseName(results);
		license.setName(nameMatch.value());
		LabelMatcher.applyFieldBox(license, "name", nameMatch);

		// 3. 类型
		LabeledMatch typeMatch = parseType(results);
		license.setType(typeMatch.value());
		LabelMatcher.applyFieldBox(license, "type", typeMatch);

		// 4. 法定代表人（含"负责人"别名）
		LabeledMatch legalMatch = parseLegalPerson(results);
		license.setLegalPerson(legalMatch.value());
		LabelMatcher.applyFieldBox(license, "legalPerson", legalMatch);

		// 5. 注册资本
		LabeledMatch capitalMatch = LabelMatcher.matchValueFromPrefixWithBox(results, "注册资本");
		license.setRegisteredCapital(capitalMatch.value());
		LabelMatcher.applyFieldBox(license, "registeredCapital", capitalMatch);

		// 6. 成立日期（优先合并框剥值，独立框兜底）
		LabeledMatch establishMatch = LabelMatcher.matchValueFromPrefixWithBox(results, "成立日期");
		if (!establishMatch.hasValue()) {
			establishMatch = LabelMatcher.matchValueWithBox(results, "成立日期");
		}
		license.setEstablishDate(establishMatch.value());
		LabelMatcher.applyFieldBox(license, "establishDate", establishMatch);

		// 7. 营业期限
		LabeledMatch periodMatch = parseOperatingPeriod(results);
		license.setOperatingPeriod(periodMatch.value());
		LabelMatcher.applyFieldBox(license, "operatingPeriod", periodMatch);

		// 8. 住所（含"营业场所"别名）
		license.setAddress(parseAddress(results));

		// 9. 经营范围（跨多行）
		license.setBusinessScope(parseBusinessScope(results));

		return license;
	}

	/**
	 * 名称提取。处理：
	 * <ul>
	 *   <li>独立"名称"标签 + 右侧 y 重叠值；</li>
	 *   <li>合并框"名称XXX有限公司" → 剥前缀；</li>
	 *   <li>合并框"称XXX有限公司"（横版常见）→ 用单字"称" fragment 标签 → 右侧值；</li>
	 *   <li>合并框"名类"（横版）→ fragment 拒绝。</li>
	 * </ul>
	 */
	private static LabeledMatch parseName(List<PPOcrV6Result> results) {
		// 1) 独立"名称"标签
		PPOcrV6Result labelBox = LabelMatcher.findCleanLabelBox(results, "名称", ALL_LABELS);
		if (labelBox != null) {
			String text = labelBox.text();
			if (text.equals("名称")) {
				LabeledMatch m = LabelMatcher.matchValueWithBox(results, "名称");
				if (m.hasValue()) return m;
			} else if (text.startsWith("名称") && text.length() > 2) {
				String stripped = text.substring(2).trim();
				if (stripped.length() >= 2) {
					log.info("营业执照解析：\"名称\" 从合并框 \"{}\" 剥出值 \"{}\"", text, stripped);
					return LabeledMatch.of(stripped, labelBox);
				}
			}
		}
		// 2) 合并框"称XXX有限公司"：用"称"作 fragment 标签，右侧 y 重叠取首值
		PPOcrV6Result chFrag = LabelMatcher.findCleanLabelBox(results, "称", ALL_LABELS);
		if (chFrag != null && "称".equals(chFrag.text())) {
			LabeledMatch m = matchRightByLabelBox(chFrag, results, null);
			if (m.hasValue()) {
				log.info("营业执照解析：\"名称\" 按 fragment \"称\" 取值 \"{}\"", m.value());
				return m;
			}
		}
		// 3) 兜底：合并框"名类" + 同行 "称XXX" → 剥前缀"称"
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("称") && text.length() > 1) {
				String stripped = text.substring(1).trim();
				if (stripped.length() >= 2) {
					log.info("营业执照解析：\"名称\" 从 \"{}\" 剥前缀 \"称\" → \"{}\"", text, stripped);
					return LabeledMatch.of(stripped, r);
				}
			}
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 类型提取。处理：
	 * <ul>
	 *   <li>独立"类型"标签；</li>
	 *   <li>合并框"类型XXX" / "型XXX" → 剥前缀；</li>
	 * </ul>
	 */
	private static LabeledMatch parseType(List<PPOcrV6Result> results) {
		// 1) 独立"类型"标签
		PPOcrV6Result labelBox = LabelMatcher.findCleanLabelBox(results, "类型", ALL_LABELS);
		if (labelBox != null) {
			String text = labelBox.text();
			if (text.equals("类型")) {
				LabeledMatch m = LabelMatcher.matchValueWithBox(results, "类型");
				if (m.hasValue()) return m;
			} else if (text.startsWith("类型") && text.length() > 2) {
				String stripped = text.substring(2).trim();
				if (stripped.length() >= 2) {
					log.info("营业执照解析：\"类型\" 从合并框 \"{}\" 剥出值 \"{}\"", text, stripped);
					return LabeledMatch.of(stripped, labelBox);
				}
			}
		}
		// 2) 合并框"型XXX"：用"型"作 fragment 标签
		PPOcrV6Result typeFrag = LabelMatcher.findCleanLabelBox(results, "型", ALL_LABELS);
		if (typeFrag != null && "型".equals(typeFrag.text())) {
			LabeledMatch m = matchRightByLabelBox(typeFrag, results, null);
			if (m.hasValue()) {
				log.info("营业执照解析：\"类型\" 按 fragment \"型\" 取值 \"{}\"", m.value());
				return m;
			}
		}
		// 3) 兜底：合并框"型XXX" → 剥前缀
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("型") && text.length() > 1 && text.length() < 60) {
				String stripped = text.substring(1).trim();
				if (stripped.length() >= 2 && stripped.length() < 60
					&& (stripped.contains("有限") || stripped.contains("责任") || stripped.contains("公司")
						|| stripped.contains("个体") || stripped.contains("个人"))) {
					log.info("营业执照解析：\"类型\" 从 \"{}\" 剥前缀 \"型\" → \"{}\"", text, stripped);
					return LabeledMatch.of(stripped, r);
				}
			}
		}
		// 4) 全文关键词兜底：OCR 漏了 "型" fragment 时（如 business3 的 "名类" 竖排二连），
		//    直接在文本里找以典型公司类型关键词开头的最长框。合法类型 keyword 优先。
		java.util.regex.Pattern TYPE_KEYWORD = java.util.regex.Pattern.compile(
			"^(有限责任公司|股份有限公司|个体工商户|个人独资企业|合伙企业|全民所有制|集体所有制|"
				+ "国有独资公司|一人有限责任公司|分公司|外商投资|中外合作|中外合资)(.*)$");
		PPOcrV6Result best = null;
		int bestLen = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			java.util.regex.Matcher mm = TYPE_KEYWORD.matcher(text);
			if (!mm.find()) continue;
			String hit = mm.group(1) + mm.group(2);
			if (hit.length() >= 3 && hit.length() > bestLen) {
				bestLen = hit.length();
				best = r;
			}
		}
		if (best != null) {
			log.info("营业执照解析：\"类型\" 全文关键词兜底命中 \"{}\"", best.text());
			return LabeledMatch.of(best.text(), best);
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 法定代表人提取（含"负责人"别名）。
	 *
	 * <p>OCR 经常把"法定代表人"标签框和值合并（"法定代表人方平"），或值漏识别（label 独立 + 值缺失）。
	 * 别名"负责人" 用于个体户执照。
	 */

	/**
	 * 其它字段 label 关键词：用于 legalPerson/address 等值框校验，
	 * 避免"营业期限" / "成立日期" 这种跨栏 label 被误选成值。
	 */
	private static final java.util.Set<String> OTHER_FIELD_LABELS = java.util.Set.of(
		"营业期限", "成立日期", "注册资本", "经营范围", "统一社会信用代码",
		"注册号", "法定代表人", "负责人", "登记机关", "证照编号", "编号", "营业执照");

	private static boolean containsOtherLabel(String text) {
		if (text == null) return false;
		for (String lbl : OTHER_FIELD_LABELS) {
			if (text.contains(lbl)) return true;
		}
		return false;
	}

	private static LabeledMatch parseLegalPerson(List<PPOcrV6Result> results) {
		for (String label : new String[]{"法定代表人", "负责人"}) {
			PPOcrV6Result labelBox = LabelMatcher.findCleanLabelBox(results, label, ALL_LABELS);
			if (labelBox == null) continue;
			String text = labelBox.text();
			if (text.equals(label)) {
				LabeledMatch m = LabelMatcher.matchValueWithBox(results, label);
				if (m.hasValue()
					&& !containsOtherLabel(m.value())
					&& m.value().length() <= 15) {  // 姓名一般短
					return m;
				}
			} else if (text.startsWith(label) && text.length() > label.length()) {
				String stripped = text.substring(label.length()).trim();
				if (stripped.length() >= 1 && stripped.length() <= 10
					&& !containsOtherLabel(stripped)) {
					log.info("营业执照解析：\"{}\" 从合并框 \"{}\" 剥出值 \"{}\"", label, text, stripped);
					return LabeledMatch.of(stripped, labelBox);
				}
			}
		}
		// 兜底：扫所有框找含"法定代表人"前缀的合并框
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("法定代表人") && text.length() > 5) {
				String stripped = text.substring(5).trim();
				if (stripped.length() >= 1 && stripped.length() <= 10
					&& !containsOtherLabel(stripped)) {
					return LabeledMatch.of(stripped, r);
				}
			}
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 营业期限提取。处理合并框（"营业期限2019年01月01日至长期"）+ 独立标签。
	 */
	private static LabeledMatch parseOperatingPeriod(List<PPOcrV6Result> results) {
		// 1) 合并框剥值
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("营业期限") && text.length() > 4) {
				String stripped = text.substring(4).trim();
				if (PERIOD_KEYWORD.matcher(stripped).find() || DATE_PATTERN.matcher(stripped).find()) {
					return LabeledMatch.of(stripped, r);
				}
			}
		}
		// 2) 独立标签右侧值
		LabeledMatch m = LabelMatcher.matchValueWithBox(results, "营业期限");
		if (m.hasValue()) return m;
		// 3) 关键字兜底（"长期"/"永久"）
		String fallback = LabelMatcher.matchPattern(results, PERIOD_KEYWORD, false);
		if (fallback != null) {
			log.info("营业执照解析：营业期限关键字兜底命中 \"{}\"", fallback);
			return LabeledMatch.textOnly(fallback);
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 住所提取（含"营业场所"别名）。处理：
	 * <ul>
	 *   <li>合并框"住所XXX" / "营业场所XXX" → 剥前缀；</li>
	 *   <li>独立标签 + 跨行值 → 拼接多行；</li>
	 *   <li>fragment 合并框（"所XXX"） → 剥前缀得 XXX。</li>
	 * </ul>
	 */
	private static String parseAddress(List<PPOcrV6Result> results) {
		String[] labels = {"住所", "营业场所"};
		for (String label : labels) {
			// 1) 合并框剥值
			for (PPOcrV6Result r : results) {
				String text = r.text();
				if (text.startsWith(label) && text.length() > label.length()) {
					String stripped = text.substring(label.length()).trim();
					if (stripped.length() >= 2) {
						log.info("营业执照解析：\"{}\" 从合并框 \"{}\" 剥出值 \"{}\"", label, text, stripped);
						return stripped;
					}
				}
			}
			// 2) 独立标签
			PPOcrV6Result labelBox = LabelMatcher.findCleanLabelBox(results, label, ALL_LABELS);
			if (labelBox != null && labelBox.text().equals(label)) {
				// 单行
				LabeledMatch m = LabelMatcher.matchValueWithBox(results, label);
				if (m.hasValue()) return m.value();
				// 多行
				String multi = LabelMatcher.collectMultiLineRight(labelBox, results, SINGLE_CHAR_FRAGMENTS);
				if (multi != null) return multi;
			}
		}
		// 3) fragment 合并框兜底："所XXX"（"所"+地址）→ 剥前缀得地址
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("所") && text.length() > 1 && !text.equals("所")) {
				String stripped = text.substring(1).trim();
				if (stripped.length() >= 2 && stripped.length() < 80) {
					log.info("营业执照解析：\"住所\" 从 fragment 合并框 \"{}\" 剥出值 \"{}\"", text, stripped);
					return stripped;
				}
			}
		}
		// 4) fragment 兜底："所"单字 → 右侧 y 重叠
		PPOcrV6Result suoFrag = LabelMatcher.findCleanLabelBox(results, "所", ALL_LABELS);
		if (suoFrag != null && "所".equals(suoFrag.text())) {
			LabeledMatch m = matchRightByLabelBox(suoFrag, results, SINGLE_CHAR_FRAGMENTS);
			if (m.hasValue()) {
				log.info("营业执照解析：\"住所\" 按 fragment \"所\" 取值 \"{}\"", m.value());
				return m.value();
			}
		}
		return null;
	}

	/**
	 * 信用代码按 "编号:XXX" / "编号：XXX" 合并框兜底。
	 *
	 * <p>某些执照版式把"编号"标签 + 信用代码识别到一个框，
	 * 截前缀后剩下的代码字符串按 {@link #CREDIT_CODE_PATTERN} 校验。
	 */
	private static LabeledMatch matchCreditCodeFromNoPrefix(List<PPOcrV6Result> results) {
		for (PPOcrV6Result r : results) {
			String text = r.text();
			String stripped = NO_PREFIX_PATTERN.matcher(text).replaceFirst("").trim();
			if (stripped.equals(text) || stripped.length() < 12) continue;
			if (CREDIT_CODE_PATTERN.matcher(stripped).matches()) {
				return LabeledMatch.of(stripped, r);
			}
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 经营范围提取。处理：
	 * <ul>
	 *   <li>合并框"经营范围XXX" → 剥前缀；</li>
	 *   <li>独立标签 + 多行值 → 拼接多行；</li>
	 *   <li>兜底：找底部最大中文文本框（含经营范围关键词、且不像登记机关）。</li>
	 * </ul>
	 */
	private static String parseBusinessScope(List<PPOcrV6Result> results) {
		// 1) 合并框剥值
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.startsWith("经营范围") && text.length() > 4) {
				String stripped = text.substring(4).trim();
				if (stripped.length() >= 2 && SCOPE_KEYWORD.matcher(stripped).find()) {
					log.info("营业执照解析：\"经营范围\" 从合并框 \"{}\" 剥出值 \"{}\"", text, stripped);
					return stripped;
				}
			}
		}
		// 2) 独立标签 → 行内水平拼接 + 多行 y 升序拼接，仅做基础合法性校验
		PPOcrV6Result labelBox = LabelMatcher.findCleanLabelBox(results, "经营范围", ALL_LABELS);
		if (labelBox != null && labelBox.text().equals("经营范围")) {
			String assembled = collectBusinessScopeByLabelBox(labelBox, results);
			if (assembled != null && assembled.length() >= 4) {
				return assembled;
			}
		}
		// 3) 兜底：底部最大中文文本框（含经营范围关键词、且不像登记机关/印章噪声）
		PPOcrV6Result best = null;
		double bestArea = 0;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.length() < 4) continue;
			if (!SCOPE_KEYWORD.matcher(text).find()) continue;
			if (text.contains("期限") || text.contains("成立") || text.contains("住所")) continue;
			// 排除明显的"登记机关"格式（如"XX市XX工商行政管理局"）
			if (text.endsWith("局") || text.contains("工商行政") || text.contains("监督管理")) continue;
			// 排除印章噪声（孤立小框，score 低）
			if (r.score() < 0.5) continue;
			int w = LabelMatcher.maxX(r) - LabelMatcher.minX(r);
			int h = LabelMatcher.maxY(r) - LabelMatcher.minY(r);
			double area = (double) w * h;
			if (area > bestArea) {
				bestArea = area;
				best = r;
			}
		}
		if (best != null) {
			log.info("营业执照解析：经营范围按底部关键词兜底命中 \"{}\"", best.text());
			return best.text();
		}
		log.warn("营业执照解析：未匹配到经营范围");
		return null;
	}

	/**
	 * 经营范围专属装配：从 label 框右侧收集 OCR 段，行内水平拼接、多行 y 升序拼接。
	 *
	 * <p>关键点：OCR 经常把一行很长、超宽的文本切成多个相邻框（[18] "体育（...开展经营" 和
	 * [19] "活动。）" 是同行的水平续段），此时不应分别取值，而应拼接为完整一行。
	 *
	 * <p>允许范围：r.centerX > label.centerX，
	 * 且 r 与 label 的 y 区间有重叠，或 r 在 label 下方最大 6 行的范围内。
	 */
	private static String collectBusinessScopeByLabelBox(PPOcrV6Result labelBox,
														 List<PPOcrV6Result> results) {
		int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		int oneLine = Math.max(1, labelMaxY - labelMinY);

		// 1. 收集候选：右侧 + y 与 label 重叠或紧邻下方最多 6 行
		//    关键: 收集完后按 y 升序排，从 label y 处顺次取，**遇 y 间距 > 1.5 行** 即视为到达下一个字段并截断。
		//    跳过其它字段的 fragment 标签框（"住"/"所"/"名"/"称"/"型"/"类" 等单字 fragment）。
		List<PPOcrV6Result> candidates = new ArrayList<>();
		java.util.Set<String> skipFragments = java.util.Set.of(
			"住", "所", "名", "称", "名类", "型", "类", "法定代表人", "负责人");
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.isEmpty()) continue;
			// 跳过其它字段的独立 fragment 标签框，以及 "名类..."/"型XXX"/"所XXX" 这种
			// 标签-值合并 fragment（这种 fragment 上下文是另一个字段，不能进入经营范围）
			if (skipFragments.contains(text)) continue;
			boolean startsWithFrag = false;
			for (String frag : skipFragments) {
				if (text.startsWith(frag) && (text.equals(frag) || text.length() - frag.length() <= 20)) {
					startsWithFrag = true;
					break;
				}
			}
			if (startsWithFrag) continue;
			int x0 = LabelMatcher.minX(r);
			int rCenterX = (x0 + LabelMatcher.maxX(r)) / 2;
			if (rCenterX <= labelCenterX) continue;
			int rMinY = LabelMatcher.minY(r);
			int rMaxY = LabelMatcher.maxY(r);
			// 放宽：y 与 label 有重叠 OR 完全在 label 下方 6 行内
			int overlap = Math.min(rMaxY, labelMaxY) - Math.max(rMinY, labelMinY);
			int gap = rMinY - labelMaxY;
			boolean yOverlap = overlap > 0;
			boolean below = gap > 0 && gap < oneLine * 6;
			if (!yOverlap && !below) continue;
			candidates.add(r);
		}
		if (candidates.isEmpty()) return null;
		// 2. 按 y 升序 + x 升序排序
		candidates.sort((a, b) -> {
			int dy = Integer.compare(LabelMatcher.minY(a), LabelMatcher.minY(b));
			if (dy != 0) return dy;
			return Integer.compare(LabelMatcher.minX(a), LabelMatcher.minX(b));
		});

		// 3. 行内水平拼接 + 行间空格
		//    关键截断: 从 label 起始向下逐段取，遇到 y 间距 > 1.5 行时即视为到达下一个字段，停止。
		StringBuilder sb = new StringBuilder();
		int prevCenterY = Integer.MIN_VALUE;
		double lineThreshold = oneLine * 1.5;
		for (PPOcrV6Result r : candidates) {
			int rCenterY = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
			int rHeight = Math.max(1, LabelMatcher.maxY(r) - LabelMatcher.minY(r));
			// 间距过大即截断
			if (prevCenterY != Integer.MIN_VALUE
				&& rCenterY - prevCenterY > lineThreshold) {
				break;
			}
			boolean sameLine = prevCenterY != Integer.MIN_VALUE
				&& Math.abs(rCenterY - prevCenterY) < Math.max(rHeight, oneLine);
			if (sb.length() == 0) {
				sb.append(r.text());
			} else if (sameLine) {
				// 同行紧邻：直接拼接（同视觉行的水平续段）
				sb.append(r.text());
			} else {
				// 换行：用空格分隔
				if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
				sb.append(r.text());
			}
			prevCenterY = rCenterY;
		}
		String result = sb.toString().trim();
		return result.isEmpty() ? null : result;
	}

	/**
	 * 给定 label 框（任意 fragment/独立标签），找右侧 y 重叠的首个值框。
	 *
	 * <p>与 LabelMatcher.matchValueByCenterWithBox 相同语义，但允许传入任意 fragment 框。
	 */
	private static LabeledMatch matchRightByLabelBox(PPOcrV6Result labelBox,
													 List<PPOcrV6Result> results,
													 Set<String> skipTexts) {
		if (labelBox == null) return LabeledMatch.textOnly(null);
		int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		PPOcrV6Result best = null;
		int bestX = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.isEmpty()) continue;
			if (skipTexts != null && skipTexts.contains(text)) continue;
			if (text.matches("[A-Za-z\\s]+")) continue;
			int x0 = LabelMatcher.minX(r);
			int rCenterX = (x0 + LabelMatcher.maxX(r)) / 2;
			if (rCenterX <= labelCenterX) continue;
			if (LabelMatcher.maxY(r) < labelMinY || LabelMatcher.minY(r) > labelMaxY) continue;
			if (x0 < bestX) {
				bestX = x0;
				best = r;
			}
		}
		return best == null ? LabeledMatch.textOnly(null) : LabeledMatch.of(best.text(), best);
	}
}
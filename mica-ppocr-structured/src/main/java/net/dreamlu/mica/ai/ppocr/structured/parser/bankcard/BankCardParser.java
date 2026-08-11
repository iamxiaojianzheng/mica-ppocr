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

package net.dreamlu.mica.ai.ppocr.structured.parser.bankcard;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 银行卡 OCR 结构化解析器。
 *
 * <p>策略概览：
 * <ul>
 *   <li><b>卡号</b>：纯正则兜底（16~19 位连续数字；支持空格分隔），不受版面影响。</li>
 *   <li><b>有效期</b>：标签定位 + {@code MM/YY} 正则校验（"VALID THRU"/"月/年"/"MONTH/YEAR"）。</li>
 *   <li><b>持卡人</b>：纯大写英文 + 可选 ". "（"MR. CHENTA"）；位置在卡号下方或有效期附近。</li>
 *   <li><b>发卡行</b>：位置在图片顶部 + 中文 ≥4 字 + 不含银联/VISA/万事达/借记/信用 等机构词。</li>
 *   <li><b>卡片类型</b>：标签定位（"CREDIT"/"DEBIT"/"信用卡"/"借记卡"）；找不到时回退到英文版本。</li>
 * </ul>
 */
@Slf4j
public class BankCardParser implements BaseStructuredParser<BankCardResult> {

	/**
	 * 单例实例。
	 */
	public static final BankCardParser INSTANCE = new BankCardParser();

	/**
	 * 卡号：去空格后 15~19 位连续数字（15 位支持 Amex 卡及 OCR 漏字场景）。
	 */
	private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("\\d{15,19}");
	/**
	 * 卡号带空格分隔版本：4-4-4-4（支持 16 位标准卡）。
	 */
	private static final Pattern CARD_NUMBER_SPACED_16 = Pattern.compile("\\d{4}\\s\\d{4}\\s\\d{4}\\s\\d{4}");
	/**
	 * 有效期 MM/YY（支持单位数月份，如 "2/27"/"02/25"）。
	 */
	private static final Pattern VALID_DATE_PATTERN = Pattern.compile("(0?[1-9]|1[0-2])/\\d{2,4}");
	/**
	 * 持卡人：大写英文 + 空格 + 可选 ". "。
	 */
	private static final Pattern HOLDER_NAME_PATTERN = Pattern.compile("[A-Z][A-Z. ]{2,30}[A-Z.]");
	/**
	 * 发卡行：中文 ≥4 字（"中国工商银行"/"华夏银行"）。
	 */
	private static final Pattern BANK_NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{4,}");
	/**
	 * 卡片类型：英文 CREDIT / DEBIT。
	 */
	private static final Pattern CARD_TYPE_EN_PATTERN = Pattern.compile("CREDIT|DEBIT", Pattern.CASE_INSENSITIVE);
	/**
	 * 卡片类型：中文 "信用卡" / "借记卡"。
	 */
	private static final Pattern CARD_TYPE_CN_PATTERN = Pattern.compile("(信用卡|借记卡)");

	/**
	 * 静态工具类风格入口。
	 *
	 * @param results OCR 结果列表
	 * @return 结构化解析结果
	 */
	public static BankCardResult parse(List<PPOcrV6Result> results) {
		return INSTANCE.parseResults(results);
	}

	@Override
	public BankCardResult parseResults(List<PPOcrV6Result> results) {
		BankCardResult r = new BankCardResult();
		r.setCardNumber(parseCardNumber(results));
		r.setValidDate(parseValidDate(results));
		r.setHolderName(parseHolderName(results));
		r.setBankName(parseBankName(results));
		r.setCardType(parseCardType(results));
		return r;
	}

	/**
	 * 卡号提取：优先匹配带空格的 4-4-4-4（16 位标准卡），
	 * 再退到 16~19 位连续数字（用于 19 位卡或空格缺失场景）。
	 *
	 * <p>支持 OCR 把分隔符识别成短横线等非数字字符的场景（如 "62223700-3333-626"）：
	 * 先清理所有非数字字符，再匹配 16~19 位数字串。
	 */
	private static String parseCardNumber(List<PPOcrV6Result> results) {
		String hit = LabelMatcher.matchPattern(results, CARD_NUMBER_SPACED_16, false);
		if (hit != null) {
			return hit.replace(" ", "");
		}
		// 清理所有非数字字符（空格、短横线等），再匹配 16~19 位连续数字
		hit = LabelMatcher.matchSubstring(results, text -> {
			String digits = text.replaceAll("[^0-9]", "");
			java.util.regex.Matcher m = CARD_NUMBER_PATTERN.matcher(digits);
			return m.find() ? m.group() : null;
		});
		if (hit != null) {
			return hit;
		}
		log.warn("银行卡解析：未匹配到卡号");
		return null;
	}

	/**
	 * 有效期提取：按"VALID THRU"/"MONTH/YEAR"/"月/年" 标签定位 + MM/YY 正则兜底。
	 */
	private static String parseValidDate(List<PPOcrV6Result> results) {
		String labelValue = LabelMatcher.matchValue(results, "VALID THRU");
		if (labelValue == null) {
			labelValue = LabelMatcher.matchValue(results, "MONTH/YEAR");
		}
		if (labelValue == null) {
			labelValue = LabelMatcher.matchValue(results, "月/年");
		}
		return LabelMatcher.labelOrFallback(labelValue, results, VALID_DATE_PATTERN, "有效期", false);
	}

	/**
	 * 持卡人提取：纯英文大写 + 空格 + ". " 模式（"MR. CHENTA"/"MR.CWENTA"）。
	 *
	 * <p>位置特征：y 范围必须落在图片下半区域（卡号/有效期附近），以避免误中
	 * 顶部银行英文简称（ICBC/CBA/CCB 等 4 字母短词）。
	 *
	 * <p>排除已知英文标签：VALID/THRU/DEBIT/CREDIT/GOLD/ETC/ATM/Quick/Pass/UnionPay/BANK 等
	 * （这些标签满足 HOLDER_NAME_PATTERN 的格式但并非持卡人姓名）。
	 */
	private static String parseHolderName(List<PPOcrV6Result> results) {
		// 已知英文标签黑名单（会被 HOLDER_NAME_PATTERN 误匹配的标签）
		java.util.Set<String> blacklist = java.util.Set.of(
			"VALID", "THRU", "DEBIT", "CREDIT", "GOLD", "ETC", "ATM",
			"Quick", "Pass", "UnionPay", "BANK", "MONTH", "YEAR",
			"DEBIT CARD", "CREDIT CARD", "MONTH/YEAR"
		);

		// 计算图片底部 y 边界
		int imgMaxY = 0;
		for (PPOcrV6Result r : results) {
			imgMaxY = Math.max(imgMaxY, LabelMatcher.maxY(r));
		}
		final int bottomY = (int) (imgMaxY * 0.5);

		String hit = LabelMatcher.matchPattern(results, text -> {
			if (!HOLDER_NAME_PATTERN.matcher(text).matches()) {
				return false;
			}
			// 排除已知英文标签（大小写敏感匹配，因为黑名单按 OCR 实际大小写录入）
			if (blacklist.contains(text)) {
				return false;
			}
			// 找到该文本对应的框，过滤掉顶部银行英文简称
			for (PPOcrV6Result r : results) {
				if (r.text().equals(text)) {
					return LabelMatcher.minY(r) >= bottomY;
				}
			}
			return true;
		}, false);
		if (hit != null) {
			return hit.replace(".", ". ").replace("  ", " ").trim();
		}
		log.warn("银行卡解析：未匹配到持卡人");
		return null;
	}

	/**
	 * 发卡行提取：图片顶部 + 中文 ≥4 字 + 排除机构词（"银联"/"信用"/"借记"/"中国"/"银行" 等单字干扰）。
	 *
	 * <p>选最顶（minY 最小）的命中框作为发卡行。
	 */
	private static String parseBankName(List<PPOcrV6Result> results) {
		// 收集所有中文 ≥4 字的候选框，按 minY 升序取第一个
		List<PPOcrV6Result> candidates = new java.util.ArrayList<>();
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (BANK_NAME_PATTERN.matcher(text).matches() && text.length() >= 4) {
				// 排除干扰项：纯机构词（不含"银行"主体）
				if (text.contains("银联") || text.equals("信用卡") || text.equals("借记卡")) {
					continue;
				}
				candidates.add(r);
			}
		}
		if (candidates.isEmpty()) {
			log.warn("银行卡解析：未匹配到发卡行");
			return null;
		}
		// 按 minY 升序，取最顶部的
		candidates.sort((a, b) -> Integer.compare(LabelMatcher.minY(a), LabelMatcher.minY(b)));
		return candidates.get(0).text();
	}

	/**
	 * 卡片类型提取：中文"信用卡/借记卡"优先，英文 CREDIT/DEBIT 兜底。
	 *
	 * <p>银行卡上类型标签常与中文/英文合并识别（如 "借记卡DEBITCARD"/"(借记卡/DEBIT)"），
	 * 因此使用子串查找（find）而非整串匹配（matches）。
	 */
	private static String parseCardType(List<PPOcrV6Result> results) {
		// 1) 中文优先：扫描所有框，找含"借记卡"或"信用卡"子串的文本
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.contains("借记卡")) return "借记卡";
			if (text.contains("信用卡")) return "信用卡";
		}
		// 2) 英文兜底：扫描所有框，找含 DEBIT 或 CREDIT 子串的文本（大小写不敏感）
		for (PPOcrV6Result r : results) {
			String text = r.text().toUpperCase();
			if (text.contains("DEBIT")) return "DEBIT";
			if (text.contains("CREDIT")) return "CREDIT";
		}
		log.warn("银行卡解析：未匹配到卡片类型");
		return null;
	}
}

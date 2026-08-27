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

package net.dreamlu.mica.ai.ppocr.structured.parser.invoice;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.LabelMatcher.LabeledMatch;
import net.dreamlu.mica.ai.ppocr.utils.CollUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增值税发票 OCR 结构化解析器。
 *
 * <p>针对中国增值税专用发票 / 普通发票（横版）版式：
 * <ul>
 *   <li><b>顶部</b>：发票代码（左侧大字） / 发票号码 No+数字（右上） / 开票日期（右下）</li>
 *   <li><b>购方区</b>：名称 / 纳税人识别号 / 地址、电话 / 开户行及账号</li>
 *   <li><b>销方区</b>：同上四字段</li>
 *   <li><b>明细表</b>：货物名称 / 规格型号 / 单位 / 数量 / 单价 / 金额 / 税率 / 税额</li>
 *   <li><b>合计行</b>：金额合计 / 税额合计</li>
 *   <li><b>价税合计</b>：（大写 ⊙ XXXX） / （小写）¥XXXX</li>
 *   <li><b>底栏</b>：收款人 / 复核 / 开票人</li>
 * </ul>
 *
 * <p>输出结果会填充 {@code InvoiceResult#getRawResults()} 与
 * {@code InvoiceResult#getFieldBoxes()}。
 */
@Slf4j
public class InvoiceParser extends BaseStructuredParser<InvoiceResult> {

	/**
	 * 发票代码：8~12 位纯数字。
	 */
	private static final Pattern INVOICE_CODE_PATTERN = Pattern.compile("^\\d{8,12}$");

	// ========================================================================
	// 正则常量
	// ========================================================================
	/**
	 * 发票号码连续数字串（≥8 位）：密码区噪音数字散落（如 +29<65>6...），
	 * 不构成连续 8 位数字，天然免疫。
	 */
	private static final Pattern INVOICE_NO_DIGITS = Pattern.compile("\\d{8,}");
	/**
	 * 开票日期：yyyy年MM月dd日 / yyyy-MM-dd / yyyy/MM/dd。
	 */
	private static final Pattern INVOICE_DATE_PATTERN = Pattern.compile(
		"\\d{4}[-./年]\\d{1,2}[-./月]\\d{1,2}日?");
	/**
	 * 大写金额关键字。
	 */
	private static final Pattern UPPER_MONEY_PATTERN = Pattern.compile(
		"[零壹贰叁肆伍陆柒捌玖拾佰仟万亿圆角分整]{3,}");
	/**
	 * 小写金额：¥1234.56 / ￥1234.56 / 1234.56。
	 */
	private static final Pattern LOWER_MONEY_PATTERN = Pattern.compile(
		"[¥￥]\\s*\\d+(?:[,，]\\d{3})*(?:\\.\\d{1,2})?");
	/**
	 * 金额数字（金额/税额栏）。
	 */
	private static final Pattern AMOUNT_NUM_PATTERN = Pattern.compile(
		"\\d+(?:[,，]\\d{3})*(?:\\.\\d{1,2})?");
	/**
	 * 税率。
	 */
	private static final Pattern TAX_RATE_PATTERN = Pattern.compile(
		"\\d{1,2}(?:\\.\\d+)?\\s*%");
	/**
	 * 合并框剥前缀后允许的标点尾巴（含 "、" 等连接符，视为标签延伸）。
	 */
	private static final Set<String> PUNCT_TAIL = CollUtil.setOf(":", "：", "、", " ", "", "、服务名称", "服务名称");

	// ========================================================================
	// 调参常量
	// ========================================================================
	/**
	 * 其它字段标签关键字集合（防止跨字段标签被当作值）。
	 */
	private static final Set<String> OTHER_LABEL_KEYWORDS = CollUtil.setOf(
		"名称", "纳税人识别号", "地址、电话", "开户行及账号",
		"货物或应税劳务", "货物或应税服务", "规格型号", "单价", "单位", "数量", "金额",
		"税率", "税额", "合计", "价税合计", "大写", "小写",
		"收款人", "复核", "开票人",
		"发票代码", "发票号码", "开票日期",
		"购买方", "销售方", "备注");

	/**
	 * 需要右侧多框拼接取值的标签：值常被 OCR 切成两个相邻框
	 * （地址 + 电话、开户行 + 账号），只取一框会丢后半段。
	 */
	private static final Set<String> JOIN_LABELS = CollUtil.setOf("地址、电话", "开户行及账号");

	/**
	 * 多框拼接间距阈值（px）：相邻值框 x 间隙 ≤ 该值才允许拼接。
	 */
	private static final int MAX_JOIN_GAP = 60;

	// ========================================================================
	// 入口
	// ========================================================================

	/**
	 * 构造增值税发票解析器，绑定推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎；可为 null（仅在仅调用 {@link #parseResults(List)} 时）
	 */
	public InvoiceParser(PPOcrV6Engine engine) {
		super(engine);
	}

	private static LabeledMatch findInvoiceCode(List<PPOcrV6Result> results) {
		// 1) 优先标签定位
		LabeledMatch m = LabelMatcher.matchValueFromPrefixWithBox(results, "发票代码");
		if (m.hasValue() && INVOICE_CODE_PATTERN.matcher(m.value()).matches()) {
			return m;
		}
		// 2) 兜底：找顶部 y 最小的 8~12 位纯数字框
		PPOcrV6Result best = null;
		int bestY = Integer.MAX_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (!INVOICE_CODE_PATTERN.matcher(text).matches()) continue;
			int y = LabelMatcher.minY(r);
			if (y < bestY) {
				bestY = y;
				best = r;
			}
		}
		if (best != null) {
			log.debug("发票解析：发票代码按顶部数字框兜底命中 \"{}\"", best.text());
			return LabeledMatch.of(best.text(), best);
		}
		return LabeledMatch.textOnly(null);
	}

	// ========================================================================
	// 顶部：发票代码 / 号码 / 日期
	// ========================================================================

	private static LabeledMatch parseInvoiceNo(List<PPOcrV6Result> results, String invoiceCode) {
		// 1) 标签 "发票号码"（仅完整/前缀标签可信；"码"等单字 fragment
		//    会被密码区噪声框污染，值框取到 "+29<65>6..." 噪音）
		if (hasInvoiceNoLabel(results)) {
			LabeledMatch m = LabelMatcher.matchValueFromPrefixWithBox(results, "发票号码");
			if (m.hasValue()) {
				String no = normalizeInvoiceNo(m.value(), invoiceCode);
				if (no != null) {
					return LabeledMatch.of(no, m.matches());
				}
			}
		}
		// 2) No/N0/Ne 前缀框：剥前缀取数字，不足 8 位时向右拼接同行数字框
		for (PPOcrV6Result r : results) {
			if (!isInvoiceNoPrefix(r.text())) continue;
			String no = matchInvoiceNoByPrefix(results, r, invoiceCode);
			if (no != null) {
				log.debug("发票解析：发票号码按前缀框 \"{}\" 取 \"{}\"", r.text(), no);
				return LabeledMatch.of(no, r);
			}
		}
		// 3) 兜底：右上区域（maxX 最大）的 8~12 位数字框，排除发票代码
		PPOcrV6Result best = null;
		int bestX = Integer.MIN_VALUE;
		for (PPOcrV6Result r : results) {
			String text = r.text().trim();
			if (!text.matches("\\d{8,12}")) continue;
			if (text.equals(invoiceCode)) continue;
			int x = LabelMatcher.maxX(r);
			if (x > bestX) {
				bestX = x;
				best = r;
			}
		}
		if (best != null) {
			log.debug("发票解析：发票号码按右上数字框兜底命中 \"{}\"", best.text());
			return LabeledMatch.of(best.text(), best);
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 是否存在完整/前缀形式的 "发票号码" 标签（fragment 不可信）。
	 */
	private static boolean hasInvoiceNoLabel(List<PPOcrV6Result> results) {
		for (PPOcrV6Result r : results) {
			String t = r.text();
			if (t.equals("发票号码") || t.startsWith("发票号码")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 判断是否为发票号码前缀框：No / N0 / Ne（OCR 常把 o 识别为 0/e）。
	 */
	private static boolean isInvoiceNoPrefix(String text) {
		return text.startsWith("No") || text.startsWith("N0") || text.startsWith("Ne");
	}

	/**
	 * 从前缀框取发票号码：剥前缀抽数字；不足 8 位时向右拼接
	 * 同行（y 重叠）且连续（gap ≤ {@link #MAX_JOIN_GAP}）的纯数字框；
	 * 号码与发票代码粘连（如 009989594200162130）时归一化取前 8 位。
	 */
	private static String matchInvoiceNoByPrefix(List<PPOcrV6Result> results,
												 PPOcrV6Result prefixBox,
												 String invoiceCode) {
		String digits = prefixBox.text().substring(2).replaceAll("\\D+", "");
		if (digits.length() < 8) {
			// 向右拼接同行数字框，凑足 8 位即止
			int prefixMinY = LabelMatcher.minY(prefixBox);
			int prefixMaxY = LabelMatcher.maxY(prefixBox);
			int curMaxX = LabelMatcher.maxX(prefixBox);
			List<PPOcrV6Result> ordered = new ArrayList<>();
			for (PPOcrV6Result r : results) {
				if (r == prefixBox) continue;
				String text = r.text().replaceAll("\\s+", "");
				if (!text.matches("\\d+")) continue;
				int x0 = LabelMatcher.minX(r);
				if (x0 <= curMaxX) continue;
				if (LabelMatcher.maxY(r) < prefixMinY || LabelMatcher.minY(r) > prefixMaxY) continue;
				if (x0 - curMaxX > MAX_JOIN_GAP) continue;
				ordered.add(r);
			}
			ordered.sort(Comparator.comparingInt(LabelMatcher::minX));
			for (PPOcrV6Result r : ordered) {
				int x0 = LabelMatcher.minX(r);
				if (x0 - curMaxX > MAX_JOIN_GAP) break;
				digits += r.text().replaceAll("\\D+", "");
				curMaxX = Math.max(curMaxX, LabelMatcher.maxX(r));
				if (digits.length() >= 8) break;
			}
		}
		return normalizeInvoiceNo(digits, invoiceCode);
	}

	/**
	 * 归一化发票号码：抽首个 ≥8 位连续数字串（号码框数字连续；
	 * 密码区噪音数字散落不构成连续 8 位，不会误命中），剥离粘连的
	 * 发票代码后缀（009989594200162130 → 00998959），取前 8 位；
	 * 不足 8 位返回 null。
	 */
	private static String normalizeInvoiceNo(String value, String invoiceCode) {
		if (value == null) return null;
		Matcher mm = INVOICE_NO_DIGITS.matcher(value);
		if (!mm.find()) return null;
		String d = mm.group();
		if (invoiceCode != null && !invoiceCode.isEmpty() && d.endsWith(invoiceCode)) {
			d = d.substring(0, d.length() - invoiceCode.length());
		}
		if (d.length() > 8) {
			d = d.substring(0, 8);
		}
		return d.length() == 8 ? d : null;
	}

	private static LabeledMatch parseInvoiceDate(List<PPOcrV6Result> results) {
		// 1) 标签 "开票日期"
		LabeledMatch m = LabelMatcher.matchValueFromPrefixWithBox(results, "开票日期");
		if (m.hasValue()) {
			Matcher mm = INVOICE_DATE_PATTERN.matcher(m.value());
			if (mm.find()) {
				return LabeledMatch.of(mm.group(), m.matches());
			}
		}
		// 2) 兜底：扫所有框取首个匹配日期正则
		return LabelMatcher.matchSubstringWithBox(results, text -> {
			Matcher mm = INVOICE_DATE_PATTERN.matcher(text);
			return mm.find() ? mm.group() : null;
		});
	}

	/**
	 * 计算图片中线 y（取所有框 y 中心的中位数）。购方区在上半部分，销方区在下半部分。
	 */
	private static int computeImageMidY(List<PPOcrV6Result> results) {
		List<Integer> centers = new ArrayList<>();
		for (PPOcrV6Result r : results) {
			centers.add((LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2);
		}
		centers.sort(Integer::compareTo);
		if (centers.isEmpty()) return 0;
		return centers.get(centers.size() / 2);
	}

	/**
	 * 判断 labelBox 是否落在指定区域（购方=上半部分；销方=下半部分）。
	 */
	private static boolean isInRegion(PPOcrV6Result labelBox, int imgMidY, boolean isBuyer) {
		int centerY = (LabelMatcher.minY(labelBox) + LabelMatcher.maxY(labelBox)) / 2;
		return isBuyer ? centerY < imgMidY : centerY >= imgMidY;
	}

	// ========================================================================
	// 购销双方四字段
	// ========================================================================

	/**
	 * 发票专用标签匹配。处理：
	 * <ul>
	 *   <li>独立标签 "名称" + 右侧 y 重叠值；</li>
	 *   <li>合并框 "名称："（仅冒号尾）→ 改走右侧 y 重叠取首值；</li>
	 *   <li>合并框 "名称XXX" → 剥前缀得 XXX；</li>
	 *   <li>fragment "名" + 右侧 y 重叠值（"称：" 单字 fragment 同样处理）。</li>
	 * </ul>
	 */
	private static LabeledMatch matchInvoiceLabel(List<PPOcrV6Result> results,
												  String[] labelAliases,
												  String debugTag,
												  int imgMidY,
												  boolean isBuyer) {
		for (String label : labelAliases) {
			// 1) 列出所有候选标签框（exact + prefix + fragment）
			List<PPOcrV6Result> candidates = findLabelBoxCandidates(results, label);
			if (candidates.isEmpty()) continue;

			for (PPOcrV6Result labelBox : candidates) {
				// y 区域过滤：候选框必须落在本方区域
				if (imgMidY > 0 && !isInRegion(labelBox, imgMidY, isBuyer)) {
					log.debug("发票解析 [{}]：标签 \"{}\" 的候选框 y={} 落在对侧，跳过",
						debugTag, label, (LabelMatcher.minY(labelBox) + LabelMatcher.maxY(labelBox)) / 2);
					continue;
				}
				String text = labelBox.text();
				LabeledMatch m = tryMatchByLabelBox(labelBox, label, debugTag, results);
				if (m.hasValue()) {
					return m;
				}
			}
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 对单个 labelBox 尝试按以下顺序取值：
	 * <ol>
	 *   <li>独立标签：右侧 y 重叠取首值</li>
	 *   <li>合并框（text 以 label 开头 → 剥前缀）：仅标点尾 → 改走右侧兜底；否则返回剥前缀值</li>
	 *   <li>fragment 标签（label 包含 text）：按 fragment 处理，右侧 y 重叠取首值（过滤短候选）</li>
	 * </ol>
	 */
	private static LabeledMatch tryMatchByLabelBox(PPOcrV6Result labelBox,
												   String label,
												   String debugTag,
												   List<PPOcrV6Result> results) {
		String text = labelBox.text();
		// 1) 独立标签
		if (text.equals(label)) {
			return matchRightValue(results, labelBox, label, false);
		}
		// 2) 合并框：text 以 label 开头
		if (text.startsWith(label) && text.length() > label.length()) {
			String stripped = text.substring(label.length());
			int s = 0;
			while (s < stripped.length() && isPunct(stripped.charAt(s))) s++;
			stripped = stripped.substring(s);
			if (stripped.isEmpty() || PUNCT_TAIL.contains(stripped)) {
				log.debug("发票解析 [{}]：标签 \"{}\" 合并框 \"{}\" 仅含标点，改走右侧 y 重叠兜底",
					debugTag, label, text);
				return matchRightValue(results, labelBox, label, false);
			}
			if (stripped.length() >= 1) {
				log.debug("发票解析 [{}]：标签 \"{}\" 从合并框 \"{}\" 剥出值 \"{}\"",
					debugTag, label, text, stripped);
				return LabeledMatch.of(stripped, labelBox);
			}
			return LabeledMatch.textOnly(null);
		}
		// 3) fragment 标签（label 包含 text）：传完整 label 用于 fragment 续段合并框剥前缀（称：值）
		if (label.contains(text) && text.length() >= 1) {
			log.debug("发票解析 [{}]：标签 \"{}\" 按 fragment \"{}\" 取右侧 y 重叠值", debugTag, label, text);
			return matchRightValue(results, labelBox, label, true);
		}
		return LabeledMatch.textOnly(null);
	}

	/**
	 * 取标签右侧值：地址电话 / 开户行账号 类标签值常被 OCR 切成多框，
	 * 走 {@link #matchRightJoinByCenter} 拼接；其余标签走单框 {@link #matchRightByCenter}。
	 *
	 * @param fragmentMode 是否 fragment 标签模式（仅对非拼接标签生效，
	 *                     传完整 label 启用续段合并框剥前缀逻辑）
	 */
	private static LabeledMatch matchRightValue(List<PPOcrV6Result> results,
												PPOcrV6Result labelBox,
												String label,
												boolean fragmentMode) {
		if (JOIN_LABELS.contains(label)) {
			return matchRightJoinByCenter(results, labelBox);
		}
		return matchRightByCenter(results, labelBox, fragmentMode ? label : null);
	}

	/**
	 * 找标签框：完整等于 / 以 label 开头 / label 包含 fragment（最长）。
	 * 用于发票场景（不引入 LabelMatcher.findLabelBox 的 fragment 日志）。
	 */
	private static PPOcrV6Result findLabelBoxAll(List<PPOcrV6Result> results, String label) {
		PPOcrV6Result exact = null;
		PPOcrV6Result prefix = null;
		PPOcrV6Result frag = null;
		int prefixLen = -1;
		int fragLen = -1;
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) continue;
			if (text.equals(label)) {
				exact = r;
			} else if (text.startsWith(label)) {
				if (text.length() > prefixLen) {
					prefixLen = text.length();
					prefix = r;
				}
			} else if (label.contains(text)) {
				if (text.length() > fragLen) {
					fragLen = text.length();
					frag = r;
				}
			}
		}
		// 优先独立框，其次最长 prefix，最后最长 fragment
		if (exact != null) return exact;
		if (prefix != null) return prefix;
		return frag;
	}

	/**
	 * 列出所有候选标签框（exact + 所有 prefix + 所有 fragment），按优先级排序：
	 * exact > prefix（长 → 短） > fragment（长 → 短）。
	 */
	private static List<PPOcrV6Result> findLabelBoxCandidates(List<PPOcrV6Result> results, String label) {
		List<PPOcrV6Result> exact = new ArrayList<>();
		List<PPOcrV6Result> prefixes = new ArrayList<>();
		List<PPOcrV6Result> frags = new ArrayList<>();
		List<PPOcrV6Result> fragmentMerged = new ArrayList<>();
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.isEmpty()) continue;
			if (text.equals(label)) {
				exact.add(r);
			} else if (text.startsWith(label)) {
				prefixes.add(r);
			} else if (label.contains(text)) {
				frags.add(r);
			} else if (text.length() > label.length() && startsWithLabelCharFollowedByPunct(label, text)) {
				// fragment 续段合并框：text 以 label 任一单字 + 标点 + 真实值构成
				fragmentMerged.add(r);
			}
		}
		prefixes.sort(Comparator.comparingInt((PPOcrV6Result r) -> r.text().length()).reversed());
		frags.sort(Comparator.comparingInt((PPOcrV6Result r) -> r.text().length()).reversed());
		List<PPOcrV6Result> all = new ArrayList<>();
		all.addAll(exact);
		all.addAll(prefixes);
		// fragmentMerged 在 frags 之前：长文本合并框剥值更可靠，避免 fragment 单字路径把整个合并框当值
		all.addAll(fragmentMerged);
		all.addAll(frags);
		return all;
	}

	/**
	 * 检测 text 是否以 label 任一单字 + 标点打头。
	 */
	private static boolean startsWithLabelCharFollowedByPunct(String label, String text) {
		for (int i = 0; i < label.length(); i++) {
			String ch = label.substring(i, i + 1);
			if (text.startsWith(ch)
				&& text.length() > ch.length()
				&& isPunct(text.charAt(ch.length()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 在给定 label 框右侧 + y 重叠区域找首个最左的值框。
	 *
	 * <p>跳过其它字段的标签框（含 {@link #OTHER_LABEL_KEYWORDS}）和纯字母/单字 fragment。
	 */
	private static LabeledMatch matchRightByCenter(List<PPOcrV6Result> results, PPOcrV6Result labelBox) {
		return matchRightByCenter(results, labelBox, null);
	}

	/**
	 * @param fullLabel 完整标签名（如"名称"），用于 fragment 模式下识别 fragment 续段合并框；
	 *                  null 时不做该过滤。
	 */
	private static LabeledMatch matchRightByCenter(List<PPOcrV6Result> results,
												   PPOcrV6Result labelBox,
												   String fullLabel) {
		boolean isFragmentLabel = fullLabel != null;
		if (labelBox == null) return LabeledMatch.textOnly(null);
		int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		int labelCenterY = (labelMinY + labelMaxY) / 2;
		int labelMinX = LabelMatcher.minX(labelBox);
		int labelMaxX = LabelMatcher.maxX(labelBox);

		PPOcrV6Result best = null;
		int bestScore = Integer.MAX_VALUE;
		// 记录最佳 fragment 续段匹配（优先于同分数普通候选）
		String bestFragmentStripped = null;
		PPOcrV6Result bestFragmentBox = null;

		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.isEmpty()) continue;
			// 跳过纯字母
			if (text.matches("[A-Za-z\\s]+")) continue;
			// 跳过其它字段标签框
			if (isOtherLabel(text)) continue;
			// fragment 模式：跳过过短的候选（避免命中另一个标签 fragment）
			if (isFragmentLabel && text.length() <= 2) continue;
			int x0 = LabelMatcher.minX(r);
			int centerX = (x0 + LabelMatcher.maxX(r)) / 2;
			int minYr = LabelMatcher.minY(r);
			int maxYr = LabelMatcher.maxY(r);
			int centerYr = (minYr + maxYr) / 2;
			// 1) 优先右侧 y 重叠
			boolean rightOverlap = centerX > labelCenterX
				&& !(maxYr < labelMinY || minYr > labelMaxY);
			// 2) fallback：x 重叠 + 在 label 正下方（同一列，行下方）
			boolean belowInColumn = (x0 <= labelMaxX && LabelMatcher.maxX(r) >= labelMinX)
				&& minYr > labelMaxY
				&& minYr < labelMaxY + (labelMaxY - labelMinY) * 3;
			if (!rightOverlap && !belowInColumn) continue;
			// 值框 x 距离约束：右侧 + y 重叠时，x0 不能离 label 太远（防止命中远处的页边标签）
			int xDistFromLabelRight = x0 > labelMaxX ? x0 - labelMaxX : 0;
			int xDistLimit = Math.max(300, (labelMaxX - labelMinX) * 3);
			if (xDistFromLabelRight > xDistLimit) continue;

			// fragment 续段检测：仅在几何约束通过后执行，避免跨区命中
			String fragmentStripped = null;
			if (isFragmentLabel && fullLabel != null) {
				for (int i = 0; i < fullLabel.length(); i++) {
					String head = fullLabel.substring(i, i + 1);
					if (text.startsWith(head)
						&& text.length() > head.length()
						&& isPunct(text.charAt(head.length()))) {
						String stripped = text.substring(head.length());
						int s = 0;
						while (s < stripped.length() && isPunct(stripped.charAt(s))) s++;
						stripped = stripped.substring(s);
						if (!stripped.isEmpty() && stripped.length() >= fullLabel.length()) {
							fragmentStripped = stripped;
							break;
						}
					}
				}
			}

			// 综合评分：越小越好
			//   y 中心差权重最高（确保同一行的值优先被取到），其次 x 距离（紧邻标签右侧优先）
			int yCenterDiff = Math.abs(centerYr - labelCenterY);
			int score;
			if (rightOverlap) {
				score = yCenterDiff * 1000 + xDistFromLabelRight;
			} else {
				// 下方 fallback 加一个大常数使优先级低于右侧
				score = 1_000_000 + (minYr - labelMaxY) * 1000 + x0;
			}
			// fragment 续段匹配：给最高优先级（score 再 -10000 保底）
			if (fragmentStripped != null) {
				score -= 10_000;
			}
			if (score < bestScore) {
				bestScore = score;
				best = r;
				bestFragmentStripped = fragmentStripped;
				bestFragmentBox = fragmentStripped != null ? r : null;
			}
		}
		if (best == null) return LabeledMatch.textOnly(null);
		if (bestFragmentStripped != null) {
			log.debug("发票解析 fragment \"{}\" 命中续段合并框 \"{}\"，剥前缀 → \"{}\"",
				fullLabel, best.text(), bestFragmentStripped);
			return LabeledMatch.of(bestFragmentStripped, bestFragmentBox);
		}
		return LabeledMatch.of(best.text(), best);
	}

	/**
	 * 右侧 y 重叠多框拼接取值（地址电话 / 开户行账号专用）。
	 *
	 * <p>值被 OCR 切成多个相邻框（如 地址 + 电话、开户行 + 账号）时，
	 * 仅取一个框会丢后半段。本方法先按 {@link #matchRightByCenter} 的评分
	 * 选出起点框（y 中心差最小、尽量紧邻标签右侧），再从起点框右边缘起
	 * 按 x 连续性（gap ≤ {@link #MAX_JOIN_GAP}）向后拼接同行的相邻框。
	 *
	 * <p>拼接仅向 x 增大方向，且要求 y 中心差 ≤ 行高（不跨行），
	 * 避免把下一行字段或标签左侧内容卷入。
	 */
	private static LabeledMatch matchRightJoinByCenter(List<PPOcrV6Result> results,
													   PPOcrV6Result labelBox) {
		if (labelBox == null) return LabeledMatch.textOnly(null);
		int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
		int labelMinX = LabelMatcher.minX(labelBox);
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		int labelCenterY = (labelMinY + labelMaxY) / 2;
		int labelMaxX = LabelMatcher.maxX(labelBox);

		// 1) 收集 label 右侧 y 重叠的候选框（过滤规则沿用 matchRightByCenter）
		List<PPOcrV6Result> candidates = new ArrayList<>();
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text();
			if (text.isEmpty()) continue;
			// 跳过纯字母
			if (text.matches("[A-Za-z\\s]+")) continue;
			// 跳过其它字段标签框
			if (isOtherLabel(text)) continue;
			int x0 = LabelMatcher.minX(r);
			int centerX = (x0 + LabelMatcher.maxX(r)) / 2;
			int minYr = LabelMatcher.minY(r);
			int maxYr = LabelMatcher.maxY(r);
			// 右侧 + y 重叠
			if (centerX <= labelCenterX) continue;
			if (maxYr < labelMinY || minYr > labelMaxY) continue;
			// x 距离约束（与 matchRightByCenter 一致）
			int xDistFromLabelRight = x0 > labelMaxX ? x0 - labelMaxX : 0;
			int xDistLimit = Math.max(300, (labelMaxX - labelMinX) * 3);
			if (xDistFromLabelRight > xDistLimit) continue;
			candidates.add(r);
		}
		if (candidates.isEmpty()) return LabeledMatch.textOnly(null);

		// 2) 起点：y 中心差最小、且尽量紧邻标签右侧（评分与 matchRightByCenter 一致）
		PPOcrV6Result start = null;
		int bestScore = Integer.MAX_VALUE;
		for (PPOcrV6Result r : candidates) {
			int centerYr = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
			int yCenterDiff = Math.abs(centerYr - labelCenterY);
			int xDistFromLabelRight = Math.max(0, LabelMatcher.minX(r) - labelMaxX);
			int score = yCenterDiff * 1000 + xDistFromLabelRight;
			if (score < bestScore) {
				bestScore = score;
				start = r;
			}
		}
		if (start == null) return LabeledMatch.textOnly(null);

		// 3) 从起点框右边缘向后连续拼接
		List<PPOcrV6Result> ordered = new ArrayList<>(candidates);
		ordered.remove(start);
		ordered.sort(Comparator.comparingInt(LabelMatcher::minX));

		List<PPOcrV6Result> joined = new ArrayList<>();
		joined.add(start);
		int prevMaxX = LabelMatcher.maxX(start);
		int startMinY = LabelMatcher.minY(start);
		int startMaxY = LabelMatcher.maxY(start);
		for (PPOcrV6Result r : ordered) {
			int x0 = LabelMatcher.minX(r);
			int minYr = LabelMatcher.minY(r);
			int maxYr = LabelMatcher.maxY(r);
			// 同行：与起点框严格垂直重叠（同一行文本框共享基线，必相交；
			// 相邻行斜框即使贴边也不相交，防止误吞上行/下行字段）
			if (maxYr <= startMinY || minYr >= startMaxY) continue;
			// 左缘窗口：新框必须紧跟当前拼接右缘（允许最大 MAX_JOIN_GAP 的重叠）。
			// 防止拼到下一行从更左侧起始的字段（如 开户行 从 x676 起始，
			// 而地址框右缘已到 x1188，二者 y 因斜框贴边重叠但列起点完全不同）
			if (x0 < prevMaxX - MAX_JOIN_GAP) continue;
			// 右向间隙：新框不能离当前右缘太远（已按 x0 升序，后续更远，直接断开）
			if (x0 - prevMaxX > MAX_JOIN_GAP) break;
			joined.add(r);
			prevMaxX = Math.max(prevMaxX, LabelMatcher.maxX(r));
		}

		if (joined.size() == 1) {
			return LabeledMatch.of(start.text(), start);
		}
		// 无分隔拼接：地址/电话、开户行/账号 本为同一字段的连续文本，OCR 切框不引入分隔
		StringBuilder sb = new StringBuilder();
		for (PPOcrV6Result r : joined) {
			sb.append(r.text().trim());
		}
		log.debug("发票解析：\"{}\" 右侧多框拼接 \"{}\"（{} 框）",
			labelBox.text(), sb, joined.size());
		return LabeledMatch.of(sb.toString(), joined);
	}

	private static boolean isOtherLabel(String text) {
		String t = text.trim();
		for (String lbl : OTHER_LABEL_KEYWORDS) {
			if (t.startsWith(lbl) || t.equals(lbl)) return true;
		}
		return false;
	}

	/**
	 * 判定字符是否属于"标签尾"标点（剥前缀时跳过）。
	 */
	private static boolean isPunct(char c) {
		return c == ':' || c == '：' || c == '、' || c == ' ' || c == ',' || c == '，';
	}

	/**
	 * 文字列（货物名称列）噪声过滤：纯数字、单字、纯标点或其它标签不取值。
	 */
	private static boolean isValidGoodsText(String text) {
		String t = text.trim();
		if (t.isEmpty() || t.length() <= 1) return false;
		if (isOtherLabel(t)) return false;
		// 纯数字（金额/税额的 fragment）跳过
		if (t.matches("\\d+")) return false;
		// 纯标点跳过
		if (t.matches("[\\p{Punct}\\s：、,，。.（）()【】\\[\\]\"\"''\\-—/\\\\]+")) return false;
		return true;
	}

	/**
	 * 残缺表头识别：表头标签被 OCR 切成单字 fragment（如 "金"+"额"、"税"+"额"）时，
	 * 收集同行内由 label 字符组成的 fragment 框，按 x 升序做子序列匹配还原表头，
	 * 返回合成框（x 跨首尾 fragment，y 为包围盒）。
	 *
	 * <p>子序列匹配保证："金额" 列只取第一个"金"后紧跟的"额"，"税额" 列跳过
	 * "金额" 列的"额"再匹配"税"+"额"，两列互不串位。
	 *
	 * <p>通过紧凑性校验（拼接宽度 ≤ label 字符数 × 2 倍行高）防止跨列/跨行误拼。
	 */
	private static PPOcrV6Result findFragmentHeaderBox(List<PPOcrV6Result> results, String label) {
		if (label.length() < 2) return null;
		List<PPOcrV6Result> candidates = new ArrayList<>();
		for (PPOcrV6Result r : results) {
			String text = r.text().replaceAll("\\s+", "");
			if (text.isEmpty() || text.length() > label.length()) continue;
			boolean allLabelChars = true;
			for (int i = 0; i < text.length(); i++) {
				if (label.indexOf(text.charAt(i)) < 0) {
					allLabelChars = false;
					break;
				}
			}
			if (allLabelChars) candidates.add(r);
		}
		if (candidates.size() < label.length()) return null;
		candidates.sort(Comparator.comparingInt(LabelMatcher::minX));
		List<PPOcrV6Result> matched = new ArrayList<>();
		int p = 0;
		int anchorMinY = Integer.MAX_VALUE;
		int anchorMaxY = Integer.MIN_VALUE;
		int anchorCenterY = 0;
		for (PPOcrV6Result r : candidates) {
			if (p == label.length()) break;
			String text = r.text().replaceAll("\\s+", "");
			if (!text.equals(String.valueOf(label.charAt(p)))) continue;
			if (matched.isEmpty()) {
				anchorMinY = LabelMatcher.minY(r);
				anchorMaxY = LabelMatcher.maxY(r);
				anchorCenterY = (anchorMinY + anchorMaxY) / 2;
			} else {
				// 后续 fragment 必须与首个 fragment 同行（y 中心差 ≤ 行高）
				int centerY = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
				int oneLine = Math.max(1, anchorMaxY - anchorMinY);
				if (Math.abs(centerY - anchorCenterY) > oneLine) continue;
			}
			matched.add(r);
			p++;
		}
		if (matched.size() < label.length()) return null;
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		float score = Float.MAX_VALUE;
		for (PPOcrV6Result r : matched) {
			minX = Math.min(minX, LabelMatcher.minX(r));
			maxX = Math.max(maxX, LabelMatcher.maxX(r));
			minY = Math.min(minY, LabelMatcher.minY(r));
			maxY = Math.max(maxY, LabelMatcher.maxY(r));
			score = Math.min(score, r.score());
		}
		// 紧凑性校验：拼接宽度不能超过 label 字符数 × 2 倍行高（防止跨列误拼）
		int oneLine = Math.max(1, maxY - minY);
		if (maxX - minX > label.length() * oneLine * 2) return null;
		int[][] box = {{minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}};
		return new PPOcrV6Result(label, score, box);
	}

	/**
	 * 找标签框（容忍空格变体），然后在表头下方找匹配 valuePattern 的多个值，按 y 升序拼接。
	 * 当 label 为"货物或应税劳务"/"货物或应税服务" 时按文字列规则（非噪声中文）取值；
	 * 其它列走正则 pattern。
	 */
	private static LabeledMatch parseTableColumn(List<PPOcrV6Result> results, String label) {
		String normalized = label.replaceAll("\\s+", "");
		// 1) 找表头
		PPOcrV6Result labelBox = null;
		String bestText = null;
		for (PPOcrV6Result r : results) {
			String text = r.text().replaceAll("\\s+", "");
			if (text.equals(normalized)) {
				labelBox = r;
				bestText = text;
				break;
			}
		}
		if (labelBox == null) {
			// 兜底A：合并框（text 以 normalized 开头，例如 "货物或应税劳务、服务名称"）
			for (PPOcrV6Result r : results) {
				String text = r.text().replaceAll("\\s+", "");
				if (text.startsWith(normalized) && text.length() > normalized.length()) {
					labelBox = r;
					bestText = text;
					break;
				}
			}
		}
		if (labelBox == null) {
			// 兜底B：残缺表头 fragment 按 x 拼接（如 "金"+"额" → "金额"、"税"+"额" → "税额"）
			PPOcrV6Result joined = findFragmentHeaderBox(results, normalized);
			if (joined != null) {
				labelBox = joined;
				bestText = label;
				log.debug("发票解析：表列 \"{}\" 采用 fragment 拼接表头（x {}~{}）",
					label, LabelMatcher.minX(joined), LabelMatcher.maxX(joined));
			}
		}
		if (labelBox == null) {
			// 兜底C：单字后缀降级（fragment 拼接失败时的最后兜底）
			// 避免匹配过短噪音：要求至少 1 个字，且属于 label 尾部子串
			int minLen = 1;
			PPOcrV6Result best = null;
			int bestOverlap = 0;
			for (PPOcrV6Result r : results) {
				String text = r.text().replaceAll("\\s+", "");
				if (text.isEmpty() || text.length() < minLen) continue;
				// 取 normalized 末尾与 text 等长的子串，比较是否相等
				if (text.length() > normalized.length()) continue;
				String suffix = normalized.substring(normalized.length() - text.length());
				if (suffix.equals(text) && text.length() > bestOverlap) {
					best = r;
					bestOverlap = text.length();
				}
			}
			if (best != null) {
				labelBox = best;
				bestText = best.text().replaceAll("\\s+", "");
				log.debug("发票解析：表列 \"{}\" 采用残缺标签 \"{}\" 作为表头", label, bestText);
			}
		}
		if (labelBox == null) return LabeledMatch.textOnly(null);

		Pattern pattern;
		switch (label) {
			case "金额":
			case "税额":
				pattern = AMOUNT_NUM_PATTERN;
				break;
			case "税率":
				pattern = TAX_RATE_PATTERN;
				break;
			default:
				pattern = null;
				break;
		}
		// 货物名称列：pattern == null 时走文字列校验
		boolean isGoodsCol = "货物或应税劳务".equals(label) || "货物或应税服务".equals(label);
		if (pattern == null && !isGoodsCol) return LabeledMatch.textOnly(null);

		int labelMinX = LabelMatcher.minX(labelBox);
		int labelMaxX = LabelMatcher.maxX(labelBox);
		int labelMinY = LabelMatcher.minY(labelBox);
		int labelMaxY = LabelMatcher.maxY(labelBox);
		int oneLine = Math.max(1, labelMaxY - labelMinY);

		// 2) 收集候选值：在表头列宽范围内 + y 与表头重叠或下方 4 行内
		//    文字列（货物名称）列允许更宽（centerX 可以到 labelMaxX + 150）
		List<PPOcrV6Result> candidates = new ArrayList<>();
		int xTolerance = isGoodsCol ? 150 : 30;
		for (PPOcrV6Result r : results) {
			if (r == labelBox) continue;
			String text = r.text().trim();
			if (text.isEmpty()) continue;
			int x0 = LabelMatcher.minX(r);
			int x1 = LabelMatcher.maxX(r);
			int centerX = (x0 + x1) / 2;
			// 列宽限制：centerX 落在表头列内或稍右
			if (centerX < labelMinX - 5 || centerX > labelMaxX + xTolerance) continue;
			int y0 = LabelMatcher.minY(r);
			// 收紧 y 下限：仅允许值框略高于表头（半行），防止误收表头上方的
			// 购/销方开户行等字段（如发票5 货物名称列误收 "中国工商银行上海市嘉定支行"）
			if (y0 < labelMinY - oneLine / 2) continue;
			if (y0 > labelMaxY + oneLine * 4) continue;
			candidates.add(r);
		}
		if (candidates.isEmpty()) return LabeledMatch.textOnly(null);

		// 3) 按 y 升序
		candidates.sort(Comparator.comparingInt(LabelMatcher::minY));

		// 4) 拼接：行内连续（间距 ≤ 1.5 行高）值用逗号分隔；不同行用换行
		StringBuilder sb = new StringBuilder();
		List<PPOcrV6Result> matches = new ArrayList<>();
		int prevY = Integer.MIN_VALUE;
		for (PPOcrV6Result r : candidates) {
			int y = (LabelMatcher.minY(r) + LabelMatcher.maxY(r)) / 2;
			boolean lineBreak = prevY != Integer.MIN_VALUE && (y - prevY) > oneLine * 1.5;
			String text = r.text().trim();
			boolean valid;
			String extracted;
			if (isGoodsCol) {
				valid = isValidGoodsText(text);
				extracted = text;
			} else {
				Matcher mm = pattern.matcher(text);
				valid = mm.find();
				extracted = valid ? mm.group() : text;
			}
			if (!valid) continue;

			if (sb.length() == 0) {
				// 第一个
			} else if (lineBreak) {
				sb.append('\n');
			} else {
				sb.append(',');
			}
			sb.append(extracted);
			matches.add(r);
			prevY = y;
		}
		String result = sb.toString();
		if (result.isEmpty()) return LabeledMatch.textOnly(null);
		log.debug("发票解析：表列 \"{}\" 拼接结果 \"{}\"（{} 行）", bestText, result, matches.size());
		return LabeledMatch.of(result, matches);
	}

	private static LabeledMatch parseTotalUpper(List<PPOcrV6Result> results) {
		// 1) 找"价税合计（大写）"标签
		PPOcrV6Result labelBox = findLabelBoxAll(results, "价税合计（大写）");
		if (labelBox == null) labelBox = findLabelBoxAll(results, "价税合计");
		if (labelBox != null) {
			LabeledMatch m = matchRightByCenter(results, labelBox);
			if (m.hasValue()) {
				String v = m.value();
				Matcher mm = UPPER_MONEY_PATTERN.matcher(v);
				if (mm.find()) {
					return LabeledMatch.of(mm.group(), m.matches());
				}
			}
		}
		// 2) 兜底：扫所有框找含连续大写金额字的
		for (PPOcrV6Result r : results) {
			Matcher mm = UPPER_MONEY_PATTERN.matcher(r.text());
			if (mm.find()) {
				String hit = mm.group();
				if (hit.length() >= 3) {
					log.debug("发票解析：价税合计大写按合并框兜底 \"{}\"", hit);
					return LabeledMatch.of(hit, r);
				}
			}
		}
		return LabeledMatch.textOnly(null);
	}

	private static LabeledMatch parseTotalLower(List<PPOcrV6Result> results) {
		// 1) "价税合计" + 右侧 (小写) + 右侧 ¥金额
		PPOcrV6Result labelBox = findLabelBoxAll(results, "价税合计");
		if (labelBox != null) {
			int labelCenterX = (LabelMatcher.minX(labelBox) + LabelMatcher.maxX(labelBox)) / 2;
			int labelMinY = LabelMatcher.minY(labelBox);
			int labelMaxY = LabelMatcher.maxY(labelBox);
			// 直接在 labelBox 下方找 ¥金额框
			PPOcrV6Result best = null;
			int bestX = Integer.MAX_VALUE;
			for (PPOcrV6Result r : results) {
				if (r == labelBox) continue;
				String text = r.text();
				if (!LOWER_MONEY_PATTERN.matcher(text).find()) continue;
				int x0 = LabelMatcher.minX(r);
				int centerX = (x0 + LabelMatcher.maxX(r)) / 2;
				if (centerX <= labelCenterX) continue;
				if (LabelMatcher.maxY(r) < labelMinY || LabelMatcher.minY(r) > labelMaxY) continue;
				if (x0 < bestX) {
					bestX = x0;
					best = r;
				}
			}
			if (best != null) {
				Matcher mm = LOWER_MONEY_PATTERN.matcher(best.text());
				if (mm.find()) {
					return LabeledMatch.of(mm.group(), best);
				}
			}
		}
		// 2) 兜底：扫所有框找 ¥金额（允许小写）
		for (PPOcrV6Result r : results) {
			String text = r.text();
			if (text.contains("小写")) continue;
			Matcher mm = LOWER_MONEY_PATTERN.matcher(text);
			if (mm.find()) {
				log.debug("发票解析：价税合计小写按 ¥ 框兜底 \"{}\"", mm.group());
				return LabeledMatch.of(mm.group(), r);
			}
		}
		return LabeledMatch.textOnly(null);
	}

	// ========================================================================
	// 明细表
	// ========================================================================

	/**
	 * 底栏匹配：复用 tryMatchByLabelBox（合并框剥前缀 + 标点尾跳过）。
	 */
	private static LabeledMatch matchFooterField(List<PPOcrV6Result> results, String label) {
		List<PPOcrV6Result> candidates = findLabelBoxCandidates(results, label);
		for (PPOcrV6Result labelBox : candidates) {
			LabeledMatch m = tryMatchByLabelBox(labelBox, label, "footer-" + label, results);
			if (m.hasValue()) return m;
		}
		return LabeledMatch.textOnly(null);
	}

	@Override
	public InvoiceResult parseResults(List<PPOcrV6Result> results) {
		return doParse(results);
	}

	private InvoiceResult doParse(List<PPOcrV6Result> results) {
		InvoiceResult r = new InvoiceResult();
		r.setRawResults(new ArrayList<>(results));

		// 1. 顶部
		parseTop(r, results);

		// 2. 购方
		parseParty(r, results, true);

		// 3. 销方
		parseParty(r, results, false);

		// 4. 明细表
		parseTable(r, results);

		// 5. 价税合计
		parseTotal(r, results);

		// 6. 底栏
		parseFooter(r, results);

		return r;
	}

	// ========================================================================
	// 价税合计
	// ========================================================================

	private void parseTop(InvoiceResult r, List<PPOcrV6Result> results) {
		// 发票代码：左侧顶部的纯数字大字
		LabeledMatch codeMatch = findInvoiceCode(results);
		r.setInvoiceCode(codeMatch.value());
		LabelMatcher.applyFieldBox(r, "invoiceCode", codeMatch);

		// 发票号码（传入发票代码：号码与代码粘连时剥离代码后缀）
		LabeledMatch noMatch = parseInvoiceNo(results, codeMatch.value());
		r.setInvoiceNo(noMatch.value());
		LabelMatcher.applyFieldBox(r, "invoiceNo", noMatch);

		// 开票日期
		LabeledMatch dateMatch = parseInvoiceDate(results);
		r.setInvoiceDate(dateMatch.value());
		LabelMatcher.applyFieldBox(r, "invoiceDate", dateMatch);
	}

	/**
	 * 解析购方或销方的"名称 / 税号 / 地址电话 / 开户行账号"四字段。
	 *
	 * @param r       结果对象
	 * @param results OCR 结果列表
	 * @param isBuyer true=购方（上半部分），false=销方（下半部分）
	 */
	private void parseParty(InvoiceResult r, List<PPOcrV6Result> results, boolean isBuyer) {
		// 计算购/销方 y 区域：购方上半部分（y < imgMidY），销方下半部分（y >= imgMidY）
		int imgMidY = computeImageMidY(results);

		// 名称：标签 "名称"（左侧）
		LabeledMatch nameMatch = matchInvoiceLabel(results, new String[]{"名称"},
			isBuyer ? "buyer-name" : "seller-name", imgMidY, isBuyer);
		if (isBuyer) {
			r.setBuyerName(nameMatch.value());
			LabelMatcher.applyFieldBox(r, "buyerName", nameMatch);
		} else {
			r.setSellerName(nameMatch.value());
			LabelMatcher.applyFieldBox(r, "sellerName", nameMatch);
		}

		// 税号：标签 "纳税人识别号"
		LabeledMatch taxMatch = matchInvoiceLabel(results, new String[]{"纳税人识别号"},
			isBuyer ? "buyer-tax" : "seller-tax", imgMidY, isBuyer);
		if (isBuyer) {
			r.setBuyerTaxNo(taxMatch.value());
			LabelMatcher.applyFieldBox(r, "buyerTaxNo", taxMatch);
		} else {
			r.setSellerTaxNo(taxMatch.value());
			LabelMatcher.applyFieldBox(r, "sellerTaxNo", taxMatch);
		}

		// 地址电话
		LabeledMatch addrMatch = matchInvoiceLabel(results, new String[]{"地址、电话"},
			isBuyer ? "buyer-addr" : "seller-addr", imgMidY, isBuyer);
		if (isBuyer) {
			r.setBuyerAddressPhone(addrMatch.value());
			LabelMatcher.applyFieldBox(r, "buyerAddressPhone", addrMatch);
		} else {
			r.setSellerAddressPhone(addrMatch.value());
			LabelMatcher.applyFieldBox(r, "sellerAddressPhone", addrMatch);
		}

		// 开户行账号
		LabeledMatch bankMatch = matchInvoiceLabel(results, new String[]{"开户行及账号"},
			isBuyer ? "buyer-bank" : "seller-bank", imgMidY, isBuyer);
		if (isBuyer) {
			r.setBuyerBankAccount(bankMatch.value());
			LabelMatcher.applyFieldBox(r, "buyerBankAccount", bankMatch);
		} else {
			r.setSellerBankAccount(bankMatch.value());
			LabelMatcher.applyFieldBox(r, "sellerBankAccount", bankMatch);
		}
	}

	/**
	 * 解析明细表四字段（货物名称 / 金额 / 税率 / 税额）。
	 */
	private void parseTable(InvoiceResult r, List<PPOcrV6Result> results) {
		// 货物名称：明细表第一列，表头 "货物或应税劳务、服务名称" 等
		LabeledMatch goodsMatch = parseTableColumn(results, "货物或应税劳务");
		if (!goodsMatch.hasValue()) {
			goodsMatch = parseTableColumn(results, "货物或应税服务");
		}
		r.setGoodsName(goodsMatch.value());
		LabelMatcher.applyFieldBox(r, "goodsName", goodsMatch);

		// 金额：表头 "金额"（可能被 OCR 加空格 "金 额"）
		LabeledMatch amountMatch = parseTableColumn(results, "金额");
		r.setAmount(amountMatch.value());
		LabelMatcher.applyFieldBox(r, "amount", amountMatch);

		// 税率：表头 "税率"
		LabeledMatch rateMatch = parseTableColumn(results, "税率");
		r.setTaxRate(rateMatch.value());
		LabelMatcher.applyFieldBox(r, "taxRate", rateMatch);

		// 税额：表头 "税额"
		LabeledMatch taxAmtMatch = parseTableColumn(results, "税额");
		r.setTaxAmount(taxAmtMatch.value());
		LabelMatcher.applyFieldBox(r, "taxAmount", taxAmtMatch);
	}

	// ========================================================================
	// 底栏
	// ========================================================================

	private void parseTotal(InvoiceResult r, List<PPOcrV6Result> results) {
		// 大写：扫所有框找含连续大写金额字的
		LabeledMatch upperMatch = parseTotalUpper(results);
		r.setTotalAmountUpper(upperMatch.value());
		LabelMatcher.applyFieldBox(r, "totalAmountUpper", upperMatch);

		// 小写：找 (小写) 标签后的 ¥ 金额
		LabeledMatch lowerMatch = parseTotalLower(results);
		r.setTotalAmountLower(lowerMatch.value());
		LabelMatcher.applyFieldBox(r, "totalAmountLower", lowerMatch);
	}

	private void parseFooter(InvoiceResult r, List<PPOcrV6Result> results) {
		// 收款人 / 复核 / 开票人 通常是合并框 "收款人：XXX"
		LabeledMatch payee = matchFooterField(results, "收款人");
		r.setPayee(payee.value());
		LabelMatcher.applyFieldBox(r, "payee", payee);

		LabeledMatch reviewer = matchFooterField(results, "复核");
		r.setReviewer(reviewer.value());
		LabelMatcher.applyFieldBox(r, "reviewer", reviewer);

		LabeledMatch issuer = matchFooterField(results, "开票人");
		r.setIssuer(issuer.value());
		LabelMatcher.applyFieldBox(r, "issuer", issuer);
	}
}

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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.ParserTestSupport;
import net.dreamlu.mica.ai.ppocr.utils.CollUtil;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 增值税发票解析器单元测试。
 *
 * <p>真实数据来源：{@code src/test/resources/ocr-json/invoice/invoice{N}.json}，
 * 由 {@link InvoiceDumpMain} 批量跑真实 OCR 推理后保存，
 * 测试时不依赖 ONNX Runtime / 模型文件，纯 Java 解析逻辑。
 */
class InvoiceParserTest extends ParserTestSupport {

	/**
	 * 统一分发器（电子版优先 → 20 位号码判别失败回退老版）：
	 * 老版 5 样本 + 空输入 + 标签缺失 mock 全部走端到端回归，并断言 version=VAT。
	 */
	private static final InvoiceParser PARSER = new InvoiceParser(null);

	/**
	 * 从 classpath 加载真实 OCR 结果（跳过 ONNX 推理，仅测试解析逻辑）。
	 */
	private static List<PPOcrV6Result> loadInvoice(String name) throws IOException {
		String path = "/ocr-json/invoice/" + name + ".json";
		List<PPOcrV6Result> list = new ArrayList<>();
		Pattern p = Pattern.compile(
			"\"text\":\"((?:[^\"\\\\]|\\\\.)*)\".*\"box\":\\[" +
				"\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\],\\[(\\d+),(\\d+)\\]\\]");
		try (InputStream is = InvoiceParserTest.class.getResourceAsStream(path);
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
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

	@Test
	void parse_emptyResults_returnsNulls() {
		InvoiceResult r = parse(PARSER, CollUtil.listOf());
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertNull(r.getInvoiceCode());
		assertNull(r.getInvoiceNo());
		assertNull(r.getInvoiceDate());
		assertNull(r.getBuyerName());
		assertNull(r.getSellerName());
		assertTrue(r.getItems().isEmpty());
		assertNull(r.getTotalAmountUpper());
		assertNull(r.getPayee());
	}

	@Test
	void parse_invoiceCodeFallbackWhenLabelMissing() {
		// "发票代码" / "发票号码" 标签缺失，按顶部数字框 + No 前缀兜底
		List<PPOcrV6Result> results = CollUtil.listOf(
			box("3100153130", 618, 420, 901, 463),
			box("No14641426", 1554, 415, 1876, 473),
			box("开票日期：2016年06月02日", 1609, 517, 1980, 552)
		);
		InvoiceResult r = parse(PARSER, results);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("3100153130", r.getInvoiceCode());
		assertEquals("14641426", r.getInvoiceNo());
		assertEquals("2016年06月02日", r.getInvoiceDate());
	}

	@Test
	void parse_invoice1() throws IOException {
		// 上海增值税发票（百度时代 → 上海易火广告，信息服务费）
		// fragment "名" + "称：" 合并框剥前缀场景
		InvoiceResult r = parse(PARSER, loadInvoice("invoice1"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("3100153130", r.getInvoiceCode());
		assertEquals("14641426", r.getInvoiceNo());
		assertEquals("2016年06月02日", r.getInvoiceDate());
		// 购买方
		assertEquals("百度时代网络技术（北京）有限公司", r.getBuyerName());
		assertEquals("110108787751579", r.getBuyerTaxNo());
		assertEquals("北京市海淀区东北旺西路8号中关村软件园17号楼二层A2010-59108001", r.getBuyerAddressPhone());
		assertEquals("招商银行北京分行大屯路支行866182028510003", r.getBuyerBankAccount());
		// 销售方
		assertEquals("上海易火广告传媒有限公司", r.getSellerName());
		assertEquals("913101140659591751", r.getSellerTaxNo());
		assertEquals("嘉定区胜辛南路500号15幢1161室55033753", r.getSellerAddressPhone());
		assertEquals("中国银行南翔支行446863841354", r.getSellerBankAccount());
		// 明细（行聚类结构化）
		assertEquals(1, r.getItems().size());
		InvoiceItem item = r.getItems().get(0);
		assertEquals("信息服务费", item.getGoodsName());
		assertEquals("94339.62", item.getAmount());
		assertEquals("6%", item.getTaxRate());
		assertEquals("5660.38", item.getTaxAmount());
		// 合计
		assertEquals("壹拾万圆整", r.getTotalAmountUpper());
		assertEquals("￥100000.00", r.getTotalAmountLower());
		// 底栏
		assertEquals("徐蓉", r.getPayee());
		assertEquals("沈园园", r.getReviewer());
		assertEquals("沈园园", r.getIssuer());
	}

	@Test
	void parse_invoice2() throws IOException {
		// 湖北增值税发票（百度在线上海分公司 → 武汉海庭假日酒店，住宿费）
		// 标签值合并框 "称：百度在线..." 一体识别场景
		InvoiceResult r = parse(PARSER, loadInvoice("invoice2"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("4200162130", r.getInvoiceCode());
		assertEquals("00998959", r.getInvoiceNo());
		assertEquals("2016年10月17日", r.getInvoiceDate());
		// 购买方
		assertEquals("百度在线网络技术（北京）有限公司上海软件技术分公司", r.getBuyerName());
		assertEquals("310114772120643", r.getBuyerTaxNo());
		assertEquals("上海市嘉定区汇荣路500号021-39005678", r.getBuyerAddressPhone());
		assertEquals("招商银行上海分行准中支行212280455510001", r.getBuyerBankAccount());
		// 销售方
		assertEquals("武汉海庭假日酒店管理有限公司", r.getSellerName());
		assertEquals("914201115879926501", r.getSellerTaxNo());
		assertEquals("武汉市洪山区民院路124号027-87598879", r.getSellerAddressPhone());
		assertEquals("交通银行武汉东湖新技术开发区支行421861636018010041548", r.getSellerBankAccount());
		// 明细（行聚类结构化）
		assertEquals(1, r.getItems().size());
		InvoiceItem item = r.getItems().get(0);
		assertEquals("住宿费", item.getGoodsName());
		assertEquals("1430.19", item.getAmount());
		assertEquals("6%", item.getTaxRate());
		assertEquals("85.81", item.getTaxAmount());
		// 合计
		assertEquals("壹仟伍佰壹拾陆圆整", r.getTotalAmountUpper());
		assertEquals("￥1516.00", r.getTotalAmountLower());
		// 底栏
		assertEquals("前台", r.getPayee());
		assertEquals("肖展", r.getReviewer());
		assertEquals("前台", r.getIssuer());
	}

	@Test
	void parse_invoice3() throws IOException {
		// 江苏增值税发票（北京糯米网 → 南京慧通酒店，住宿费）
		InvoiceResult r = parse(PARSER, loadInvoice("invoice3"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("3200153130", r.getInvoiceCode());
		assertEquals("44071097", r.getInvoiceNo());
		assertEquals("2016年10月17日", r.getInvoiceDate());
		// 购买方
		assertEquals("北京糯米网科技发展有限公司", r.getBuyerName());
		assertEquals("110108787758500", r.getBuyerTaxNo());
		assertEquals("北京市海淀区中关村南大街甲10号银海大厦七层南719A室010-84481818", r.getBuyerAddressPhone());
		assertEquals("招商银行北京东三环支行861185196210001", r.getBuyerBankAccount());
		// 销售方
		assertEquals("南京慧通酒店管理有限责任公司", r.getSellerName());
		assertEquals("91320114302511244L", r.getSellerTaxNo());
		assertEquals("南京市雨花台区安德门大街57号2幢025-86980999", r.getSellerAddressPhone());
		assertEquals("中国工商银行江苏省分行营业部4301016509100393377", r.getSellerBankAccount());
		// 明细（行聚类结构化）
		assertEquals(1, r.getItems().size());
		InvoiceItem item = r.getItems().get(0);
		assertEquals("住宿费", item.getGoodsName());
		assertEquals("377.36", item.getAmount());
		assertEquals("6%", item.getTaxRate());
		assertEquals("22.64", item.getTaxAmount());
		// 合计
		assertEquals("肆佰圆整", r.getTotalAmountUpper());
		assertEquals("￥400.00", r.getTotalAmountLower());
		// 底栏
		assertEquals("高梦雅", r.getPayee());
		assertEquals("梁笑", r.getReviewer());
		assertEquals("孙莉琼", r.getIssuer());
	}

	@Test
	void parse_invoice4() throws IOException {
		// 北京增值税发票（北京百度网讯 → 北京圣紫茗管理咨询，服务费）
		// 收款人标签后无人名 → null
		InvoiceResult r = parse(PARSER, loadInvoice("invoice4"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("1100154130", r.getInvoiceCode());
		assertEquals("00772445", r.getInvoiceNo());
		assertEquals("2016年11月15日", r.getInvoiceDate());
		// 购买方
		assertEquals("北京百度网讯科技有限公司", r.getBuyerName());
		assertEquals("110108802100433", r.getBuyerTaxNo());
		assertEquals("北京市海淀区上地十街10号百度大厦2层010-59928888", r.getBuyerAddressPhone());
		assertEquals("招商银行北京分行上地支行110902160610706", r.getBuyerBankAccount());
		// 销售方
		assertEquals("北京圣紫茗管理容询有限公司", r.getSellerName());
		assertEquals("110105057317113", r.getSellerTaxNo());
		assertEquals("北京市朝阳区64377727", r.getSellerAddressPhone());
		assertEquals("上海浦发银行91150154740007408", r.getSellerBankAccount());
		// 明细（行聚类结构化）
		assertEquals(1, r.getItems().size());
		InvoiceItem item = r.getItems().get(0);
		assertEquals("服务费", item.getGoodsName());
		assertEquals("5785.38", item.getAmount());
		assertEquals("6%", item.getTaxRate());
		assertEquals("347.12", item.getTaxAmount());
		// 合计
		assertEquals("陆仟壹佰叁拾贰圆伍角整", r.getTotalAmountUpper());
		assertEquals("￥6132.50", r.getTotalAmountLower());
		// 底栏
		assertNull(r.getPayee());
		assertEquals("马学琦", r.getReviewer());
		assertEquals("焦红娟", r.getIssuer());
	}

	@Test
	void parse_invoice5() throws IOException {
		// 安徽增值税发票（上海优扬新媒 → 合肥乐堂动漫，信息费）
		// "金额" 表头残缺为 "额" 单字场景
		InvoiceResult r = parse(PARSER, loadInvoice("invoice5"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.VAT, r.getVersion());
		assertEquals("3400161130", r.getInvoiceCode());
		assertEquals("00666375", r.getInvoiceNo());
		assertEquals("2016年11月11日", r.getInvoiceDate());
		// 购买方
		assertEquals("上海优扬新媒信息技术有限公司", r.getBuyerName());
		assertEquals("91310114585239729M", r.getBuyerTaxNo());
		assertEquals("上海市嘉定区工业区汇源路55号H幢3层A区021-63460206", r.getBuyerAddressPhone());
		assertEquals("中国工商银行上海市嘉定支行1001700819300415148", r.getBuyerBankAccount());
		// 销售方
		assertEquals("合肥乐堂动漫信息技术有限公司", r.getSellerName());
		assertEquals("91340100686877076E", r.getSellerTaxNo());
		assertEquals("合肥市金寨路71号科茂大厦5层0551-65411799", r.getSellerAddressPhone());
		assertEquals("招商银行合肥南七支行551903169110102", r.getSellerBankAccount());
		// 明细（行聚类结构化）
		assertEquals(1, r.getItems().size());
		InvoiceItem item = r.getItems().get(0);
		assertEquals("信息费", item.getGoodsName());
		assertEquals("2524.75", item.getAmount());
		assertEquals("6%", item.getTaxRate());
		assertEquals("151.49", item.getTaxAmount());
		// 合计
		assertEquals("贰仟陆佰柒拾陆圆贰角肆分", r.getTotalAmountUpper());
		assertEquals("￥2676.24", r.getTotalAmountLower());
		// 底栏
		assertEquals("李平", r.getPayee());
		assertEquals("李平", r.getReviewer());
		assertEquals("秦丽萍", r.getIssuer());
	}

	// ========================================================================
	// 新版电子发票（数电票）用例
	// ========================================================================

	/**
	 * 直测判别器：空输入 → electronic 返回 null（分发器回退老版）。
	 */
	@Test
	void electronic_emptyResults_returnsNull() {
		assertNull(new ElectronicInvoiceParser().parseResults(CollUtil.listOf()));
	}

	/**
	 * 直测判别器：老版增值税发票 OCR 结果中无 20 位连续数字 → 返回 null。
	 */
	@Test
	void electronic_vatResults_returnsNull() throws IOException {
		assertNull(new ElectronicInvoiceParser().parseResults(loadInvoice("invoice1")));
	}

	/**
	 * 数电票端到端：合肥 → 合肥，旅客运输服务。
	 *
	 * <p>OCR 已知瑕疵（解析器如实透传，不做修正）：
	 * <ul>
	 *   <li>买名称"锐域"误识为"皖域"；开票人"鋆"误识为"寒"</li>
	 *   <li>税率列"3%"误识为"3%6"（正则按 % 截断）</li>
	 *   <li>小写金额前缀"¥"误识为"?"（归一化为 ¥）</li>
	 * </ul>
	 */
	@Test
	void parse_electronic_invoice_elec1() throws IOException {
		InvoiceResult r = parse(PARSER, loadInvoice("invoice6"));
		assertNotNull(r);
		assertEquals(InvoiceVersion.ELECTRONIC, r.getVersion());

		// 顶部
		assertEquals("26347000000187619471", r.getInvoiceNo());
		assertEquals("2026年07月28日", r.getInvoiceDate());

		// 购方
		assertEquals("合肥皖域信息科技有限公司", r.getBuyerName());
		assertEquals("913401000723997351", r.getBuyerTaxNo());

		// 销方
		assertEquals("合肥吉利优行科技有限公司", r.getSellerName());
		assertEquals("91340100MA2MRTW78F", r.getSellerTaxNo());

		// 明细表（行聚类结构化，2 行）
		assertEquals(2, r.getItems().size());
		InvoiceItem row0 = r.getItems().get(0);
		assertEquals("交通运输服务*客运服务费", row0.getGoodsName());
		assertEquals("24.49", row0.getAmount());
		assertEquals("3%", row0.getTaxRate());
		assertEquals("0.73", row0.getTaxAmount());
		InvoiceItem row1 = r.getItems().get(1);
		assertEquals("交通运输服务*客运服务费", row1.getGoodsName());
		assertEquals("-3.33", row1.getAmount());
		assertEquals("3%", row1.getTaxRate());
		assertEquals("-0.10", row1.getTaxAmount());

		// 价税合计
		assertEquals("贰拾壹圆柒角玖分", r.getTotalAmountUpper());
		assertEquals("¥21.79", r.getTotalAmountLower());

		// 备注（空）
		assertNull(r.getRemark());

		// 底栏（仅开票人）
		assertNull(r.getPayee());
		assertNull(r.getReviewer());
		assertEquals("钟寒冰", r.getIssuer());
	}
}

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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 增值税发票解析器单元测试。
 *
 * <p>真实数据来源：{@code src/test/resources/ocr-json/invoice/invoice{N}.json}，
 * 由 {@link InvoiceDumpMain} 批量跑真实 OCR 推理后保存，
 * 测试时不依赖 ONNX Runtime / 模型文件，纯 Java 解析逻辑。
 */
class InvoiceParserTest extends ParserTestSupport {

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
		InvoiceResult r = parse(new InvoiceParser(null), List.of());
		assertNotNull(r);
		assertNull(r.getInvoiceCode());
		assertNull(r.getInvoiceNo());
		assertNull(r.getInvoiceDate());
		assertNull(r.getBuyerName());
		assertNull(r.getSellerName());
		assertNull(r.getGoodsName());
		assertNull(r.getTotalAmountUpper());
		assertNull(r.getPayee());
	}

	@Test
	void parse_invoiceCodeFallbackWhenLabelMissing() {
		// "发票代码" / "发票号码" 标签缺失，按顶部数字框 + No 前缀兜底
		List<PPOcrV6Result> results = List.of(
			box("3100153130", 618, 420, 901, 463),
			box("No14641426", 1554, 415, 1876, 473),
			box("开票日期：2016年06月02日", 1609, 517, 1980, 552)
		);
		InvoiceResult r = parse(new InvoiceParser(null), results);
		assertEquals("3100153130", r.getInvoiceCode());
		assertEquals("14641426", r.getInvoiceNo());
		assertEquals("2016年06月02日", r.getInvoiceDate());
	}

	@Test
	void parse_invoice1() throws IOException {
		// 上海增值税发票（百度时代 → 上海易火广告，信息服务费）
		// fragment "名" + "称：" 合并框剥前缀场景
		InvoiceResult r = parse(new InvoiceParser(null), loadInvoice("invoice1"));
		assertNotNull(r);
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
		// 明细
		assertEquals("信息服务费", r.getGoodsName());
		assertEquals("94339.62", r.getAmount());
		assertEquals("6%", r.getTaxRate());
		assertEquals("5660.38", r.getTaxAmount());
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
		InvoiceResult r = parse(new InvoiceParser(null), loadInvoice("invoice2"));
		assertNotNull(r);
		assertEquals("4200162130", r.getInvoiceCode());
		assertEquals("00998959", r.getInvoiceNo());
		assertEquals("2016年10月17日", r.getInvoiceDate());
		// 购买方
		assertEquals("百度在线网络技术（北京）有限公司上海软件技术分公司", r.getBuyerName());
		assertEquals("310114772120643", r.getBuyerTaxNo());
		assertEquals("上海市嘉定区汇荣路500号021-39005678", r.getBuyerAddressPhone());
		assertEquals("招商银行上海分行淮中支行212280455510001", r.getBuyerBankAccount());
		// 销售方
		assertEquals("武汉海庭假日酒店管理有限公司", r.getSellerName());
		assertEquals("914201115879926501", r.getSellerTaxNo());
		assertEquals("武汉市洪山区民院路124号027-87598879", r.getSellerAddressPhone());
		assertEquals("交通银行武汉东湖新技术开发区支行421861636018010041548", r.getSellerBankAccount());
		// 明细
		assertEquals("住宿费", r.getGoodsName());
		assertEquals("1430.19", r.getAmount());
		assertEquals("6%", r.getTaxRate());
		assertEquals("85.81", r.getTaxAmount());
		// 合计
		assertEquals("壹仟伍佰壹拾陆圆整", r.getTotalAmountUpper());
		assertEquals("￥1516.00", r.getTotalAmountLower());
		// 底栏
		assertEquals("前台", r.getPayee());
		assertEquals("肖晨", r.getReviewer());
		assertEquals("前台", r.getIssuer());
	}

	@Test
	void parse_invoice3() throws IOException {
		// 江苏增值税发票（北京糯米网 → 南京慧通酒店，住宿费）
		InvoiceResult r = parse(new InvoiceParser(null), loadInvoice("invoice3"));
		assertNotNull(r);
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
		// 明细
		assertEquals("住宿费", r.getGoodsName());
		assertEquals("377.36", r.getAmount());
		assertEquals("6%", r.getTaxRate());
		assertEquals("22.64", r.getTaxAmount());
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
		InvoiceResult r = parse(new InvoiceParser(null), loadInvoice("invoice4"));
		assertNotNull(r);
		assertEquals("1100154130", r.getInvoiceCode());
		assertEquals("00772445", r.getInvoiceNo());
		assertEquals("2016年11月15日", r.getInvoiceDate());
		// 购买方
		assertEquals("北京百度网讯科技有限公司", r.getBuyerName());
		assertEquals("110108802100433", r.getBuyerTaxNo());
		assertEquals("北京市海淀区上地十街10号百度大厦2层010-59928888", r.getBuyerAddressPhone());
		assertEquals("招商银行北京分行上地支行110902160610706", r.getBuyerBankAccount());
		// 销售方
		assertEquals("北京圣紫茗管理咨询有限公司", r.getSellerName());
		assertEquals("110105057317113", r.getSellerTaxNo());
		assertEquals("北京市朝阳区64377727", r.getSellerAddressPhone());
		assertEquals("上海浦发银行91150154740007408", r.getSellerBankAccount());
		// 明细
		assertEquals("服务费", r.getGoodsName());
		assertEquals("5785.38", r.getAmount());
		assertEquals("6%", r.getTaxRate());
		assertEquals("347.12", r.getTaxAmount());
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
		InvoiceResult r = parse(new InvoiceParser(null), loadInvoice("invoice5"));
		assertNotNull(r);
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
		// 明细
		assertEquals("信息费", r.getGoodsName());
		assertEquals("2524.75", r.getAmount());
		assertEquals("6%", r.getTaxRate());
		assertEquals("151.49", r.getTaxAmount());
		// 合计
		assertEquals("贰仟陆佰柒拾陆圆贰角肆分", r.getTotalAmountUpper());
		assertEquals("￥2676.24", r.getTotalAmountLower());
		// 底栏
		assertEquals("李平", r.getPayee());
		assertEquals("李平", r.getReviewer());
		assertEquals("秦丽萍", r.getIssuer());
	}
}

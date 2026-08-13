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
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

import java.util.List;

/**
 * 发票结构化解析调试入口。
 *
 * <p>替换 {@code IMAGE_PATH} 为待调试的发票图片，运行 main 即可输出 OCR 框 + 结构化字段。
 */
public class InvoiceMain extends BaseTest {

	/**
	 * 推理图片路径，相对工程根目录
	 */
	private static final String IMAGE_PATH = "test_images/invoice/invoice1.jpg";
	/**
	 * 可视化输出路径；传 null 跳过可视化
	 */
	private static final String VIS_PATH = "test_images/invoice/vis.png";

	public static void main(String[] args) {
		new InvoiceMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected void printResults(List<PPOcrV6Result> results) {
		InvoiceResult inv = InvoiceParser.parse(results);
		System.out.println("\n--- 发票结构化解析 ---");
		System.out.println("发票代码       " + inv.getInvoiceCode());
		System.out.println("发票号码       " + inv.getInvoiceNo());
		System.out.println("开票日期       " + inv.getInvoiceDate());
		System.out.println();
		System.out.println("--- 购买方 ---");
		System.out.println("名称           " + inv.getBuyerName());
		System.out.println("税号           " + inv.getBuyerTaxNo());
		System.out.println("地址电话       " + inv.getBuyerAddressPhone());
		System.out.println("开户行账号     " + inv.getBuyerBankAccount());
		System.out.println();
		System.out.println("--- 销售方 ---");
		System.out.println("名称           " + inv.getSellerName());
		System.out.println("税号           " + inv.getSellerTaxNo());
		System.out.println("地址电话       " + inv.getSellerAddressPhone());
		System.out.println("开户行账号     " + inv.getSellerBankAccount());
		System.out.println();
		System.out.println("--- 明细 ---");
		System.out.println("商品/服务名称  " + inv.getGoodsName());
		System.out.println("金额           " + inv.getAmount());
		System.out.println("税率           " + inv.getTaxRate());
		System.out.println("税额           " + inv.getTaxAmount());
		System.out.println();
		System.out.println("--- 合计 ---");
		System.out.println("价税合计(大写) " + inv.getTotalAmountUpper());
		System.out.println("价税合计(小写) " + inv.getTotalAmountLower());
		System.out.println();
		System.out.println("--- 底栏 ---");
		System.out.println("收款人         " + inv.getPayee());
		System.out.println("复核人         " + inv.getReviewer());
		System.out.println("开票人         " + inv.getIssuer());
	}
}

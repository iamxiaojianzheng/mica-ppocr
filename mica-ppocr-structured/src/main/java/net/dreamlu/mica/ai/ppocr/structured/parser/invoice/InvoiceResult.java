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

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredResult;

/**
 * 增值税发票 OCR 结构化解析结果（按用户字段清单：发票代码/号码/日期，
 * 购销双方名称/税号/地址电话/开户行账号，商品/金额/税率/税额，
 * 价税合计大写与小写，收款人/复核人/开票人）。
 *
 * <p>继承 {@link BaseStructuredResult}：
 * <ul>
 *   <li>{@code rawResults} —— 原始 OCR 结果（含所有文字框）</li>
 *   <li>{@code fieldBoxes} —— 字段名 → 对应 OCR 框坐标（key 见各字段上方注释）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InvoiceResult extends BaseStructuredResult {
	/**
	 * 发票代码
	 */
	private String invoiceCode;
	/**
	 * 发票号码
	 */
	private String invoiceNo;
	/**
	 * 开票日期
	 */
	private String invoiceDate;

	/**
	 * 购买方名称
	 */
	private String buyerName;
	/**
	 * 购买方税号（统一社会信用代码/纳税人识别号）
	 */
	private String buyerTaxNo;
	/**
	 * 购买方地址、电话
	 */
	private String buyerAddressPhone;
	/**
	 * 购买方开户行、账号
	 */
	private String buyerBankAccount;

	/**
	 * 销售方名称
	 */
	private String sellerName;
	/**
	 * 销售方税号
	 */
	private String sellerTaxNo;
	/**
	 * 销售方地址、电话
	 */
	private String sellerAddressPhone;
	/**
	 * 销售方开户行、账号
	 */
	private String sellerBankAccount;

	/**
	 * 商品/服务名称（多行合并为单字符串，用换行分隔）
	 */
	private String goodsName;
	/**
	 * 金额（多行合计字符串）
	 */
	private String amount;
	/**
	 * 税率（多行合并字符串）
	 */
	private String taxRate;
	/**
	 * 税额（多行合并字符串）
	 */
	private String taxAmount;

	/**
	 * 价税合计（大写）
	 */
	private String totalAmountUpper;
	/**
	 * 价税合计（小写）
	 */
	private String totalAmountLower;

	/**
	 * 收款人
	 */
	private String payee;
	/**
	 * 复核人
	 */
	private String reviewer;
	/**
	 * 开票人
	 */
	private String issuer;
}

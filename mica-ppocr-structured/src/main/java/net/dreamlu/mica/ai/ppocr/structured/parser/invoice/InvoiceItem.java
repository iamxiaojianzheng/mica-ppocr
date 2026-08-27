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

/**
 * 发票明细行（新版电子发票 / 老版增值税发票共用结构化明细）。
 *
 * <p>由 {@link InvoiceTableParser} 行聚类产出：同一 y 行的 OCR 框按 x 分配到各列，
 * 列对齐天然正确，不再存在"各列行数不一致"的错位问题。
 *
 * <p>字段值为字符串（透传 OCR 结果，保留千分位/负数等原始形态），
 * 无该列（如老版无单价/数量）或该行该列无值时为空。
 */
@Data
public class InvoiceItem {
	/**
	 * 项目名称 / 货物或应税劳务（服务）名称
	 */
	private String goodsName;
	/**
	 * 单价
	 */
	private String unitPrice;
	/**
	 * 数量
	 */
	private String quantity;
	/**
	 * 金额
	 */
	private String amount;
	/**
	 * 税率/征收率
	 */
	private String taxRate;
	/**
	 * 税额
	 */
	private String taxAmount;
}

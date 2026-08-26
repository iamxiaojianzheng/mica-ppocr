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

package net.dreamlu.mica.ai.ppocr.structured.parser.taxi;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredResult;

/**
 * 出租车票 OCR 结构化解析结果（对齐百度 OCR 出租车票接口字段）。
 *
 * <p>典型字段（12 个）：
 * <ul>
 *   <li><b>票号</b>：发票代码（12 位）、发票号码（8 位）</li>
 *   <li><b>行程</b>：车牌号、日期、上车时间、下车时间、里程</li>
 *   <li><b>金额</b>：金额、燃油附加费、叫车服务费、总金额</li>
 *   <li><b>其他</b>：开票城市</li>
 * </ul>
 *
 * <p>继承 {@link BaseStructuredResult}：
 * <ul>
 *   <li>{@code rawResults} ——原始 OCR 结果（含所有文字框）</li>
 *   <li>{@code fieldBoxes} ——字段名 → 对应 OCR 框坐标（key 见各字段上方注释）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaxiReceiptResult extends BaseStructuredResult {

	// ========================================================================
	// 票号
	// ========================================================================

	/**
	 * 发票代码（12 位数字，如 111001981002）
	 */
	private String invoiceCode;
	/**
	 * 发票号码（8 位数字）
	 */
	private String invoiceNo;

	// ========================================================================
	// 行程
	// ========================================================================

	/**
	 * 车牌号（如 京A12345、BU1346）
	 */
	private String plateNumber;
	/**
	 * 日期（格式 yyyy-MM-dd / yyyy年MM月dd日）
	 */
	private String date;
	/**
	 * 上车时间（格式 HH:mm）
	 */
	private String boardingTime;
	/**
	 * 下车时间（格式 HH:mm）
	 */
	private String alightingTime;
	/**
	 * 里程（公里，保留 1 位小数）
	 */
	private String mileage;

	// ========================================================================
	// 金额
	// ========================================================================

	/**
	 * 金额（计费金额，不含附加费）
	 */
	private String amount;
	/**
	 * 燃油附加费
	 */
	private String fuelSurcharge;
	/**
	 * 叫车服务费
	 */
	private String bookingFee;
	/**
	 * 总金额（含附加费）
	 */
	private String totalAmount;

	// ========================================================================
	// 其他
	// ========================================================================

	/**
	 * 开票城市
	 */
	private String city;
}

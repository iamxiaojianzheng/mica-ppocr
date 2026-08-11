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

package net.dreamlu.mica.ai.ppocr.structured.parser.vehicle;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredResult;

/**
 * 行驶证 OCR 结构化解析结果。
 *
 * <p>继承 {@link BaseStructuredResult}：
 * <ul>
 *   <li>{@code rawResults} —— 原始 OCR 结果（含所有文字框）</li>
 *   <li>{@code fieldBoxes} —— 字段名 → 对应 OCR 框坐标（key: plateNo/owner/vehicleType/vin/issueDate）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VehicleLicenseResult extends BaseStructuredResult {
	/**
	 * 车牌号码
	 */
	private String plateNo;
	/**
	 * 所有人
	 */
	private String owner;
	/**
	 * 车辆类型
	 */
	private String vehicleType;
	/**
	 * 车架号/车辆识别代号
	 */
	private String vin;
	/**
	 * 发证日期
	 */
	private String issueDate;
}

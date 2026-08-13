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

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredResult;

/**
 * 营业执照 OCR 结构化解析结果。
 *
 * <p>典型字段：社会信用代码、单位名称、住址、法定代表人、有效日期至、成立日期、
 * 类型、注册资本、经营范围。
 *
 * <p>继承 {@link BaseStructuredResult}：
 * <ul>
 *   <li>{@code rawResults} —— 原始 OCR 结果（含所有文字框）</li>
 *   <li>{@code fieldBoxes} —— 字段名 → 对应 OCR 框坐标（key: creditCode/name/address/legalPerson/operatingPeriod/establishDate/type/registeredCapital/businessScope）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessLicenseResult extends BaseStructuredResult {
	/**
	 * 社会信用代码（18 位字母+数字）
	 */
	private String creditCode;
	/**
	 * 单位名称
	 */
	private String name;
	/**
	 * 类型（如：有限责任公司(自然人投资或控股)）
	 */
	private String type;
	/**
	 * 法定代表人
	 */
	private String legalPerson;
	/**
	 * 注册资本
	 */
	private String registeredCapital;
	/**
	 * 成立日期
	 */
	private String establishDate;
	/**
	 * 有效日期至（如：长期 / 2020-01-01 至 2050-12-31）
	 */
	private String operatingPeriod;
	/**
	 * 住址
	 */
	private String address;
	/**
	 * 经营范围
	 */
	private String businessScope;
}
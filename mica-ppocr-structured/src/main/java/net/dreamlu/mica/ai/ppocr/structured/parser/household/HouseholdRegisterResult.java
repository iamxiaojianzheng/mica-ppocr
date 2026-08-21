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

package net.dreamlu.mica.ai.ppocr.structured.parser.household;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredResult;

/**
 * 户口本（常住人口登记卡）OCR 结构化解析结果。
 *
 * <p>字段对照表（精简版，仅 15 个核心字段）：
 * <ul>
 *   <li>{@link #householdNo 户号} —— 顶部独立编号</li>
 *   <li>{@link #name 姓名}</li>
 *   <li>{@link #relationship 与户主关系}</li>
 *   <li>{@link #gender 性别}</li>
 *   <li>{@link #birthPlace 出生地}</li>
 *   <li>{@link #ethnicity 民族}</li>
 *   <li>{@link #nativePlace 籍贯}</li>
 *   <li>{@link #birthDate 出生日期}</li>
 *   <li>{@link #idNumber 公民身份号码}</li>
 *   <li>{@link #height 身高}</li>
 *   <li>{@link #education 文化程度}</li>
 *   <li>{@link #workplace 服务处所}</li>
 *   <li>{@link #moveToCityDate 何时由何地迁来本市(县)}</li>
 *   <li>{@link #moveToAddress 何时由何地迁往本址}</li>
 *   <li>{@link #registrationDate 登记日期}</li>
 * </ul>
 *
 * <p>继承 {@link BaseStructuredResult}：
 * <ul>
 *   <li>{@code rawResults} —— 原始 OCR 结果（含所有文字框）</li>
 *   <li>{@code fieldBoxes} —— 字段名 → 对应 OCR 框坐标（key 即上面表格中的小写驼峰字段名）</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HouseholdRegisterResult extends BaseStructuredResult {

	/**
	 * 户号（7~12 位数字，例 "000007670"）
	 */
	private String householdNo;
	/**
	 * 姓名
	 */
	private String name;
	/**
	 * 与户主关系（户主 / 夫 / 妻 / 子 / 女 / 父 / 母 / 兄 / 弟 / 姐 / 妹 / 独生女 / 独生子 等）
	 */
	private String relationship;
	/**
	 * 性别（男 / 女）
	 */
	private String gender;
	/**
	 * 出生地（如"四川省"）
	 */
	private String birthPlace;
	/**
	 * 民族（如"汉族"或"汉"）
	 */
	private String ethnicity;
	/**
	 * 籍贯（如"四川省"）
	 */
	private String nativePlace;
	/**
	 * 出生日期（"yyyy 年 MM 月 dd 日" 或 "yyyyMMdd"）
	 */
	private String birthDate;
	/**
	 * 公民身份号码（18 位）
	 */
	private String idNumber;
	/**
	 * 身高（如"170厘米"/"160"）
	 */
	private String height;
	/**
	 * 文化程度（小学 / 初中 / 高中 / 中专 / 大专 / 大学 / 初中毕业 / 小学毕业 等）
	 */
	private String education;
	/**
	 * 服务处所（工作单位 / "无"）
	 */
	private String workplace;
	/**
	 * 何时由何地迁来本市(县)（如"由久居"/"由江西省南昌市迁来"/"2009年09月29日"）
	 */
	private String moveToCityDate;
	/**
	 * 何时由何地迁往本址（如"1994年07月27日因出生迁来"/"因购房"）
	 */
	private String moveToAddress;
	/**
	 * 登记日期（"yyyy 年 MM 月 dd 日"）
	 */
	private String registrationDate;
}
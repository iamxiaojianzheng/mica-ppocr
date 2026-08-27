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

package net.dreamlu.mica.ai.ppocr.structured.parser.batch;

import lombok.Value;
import lombok.experimental.Accessors;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.household.HouseholdRegisterParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.pdd.PddLuckyBagParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 解析器规格：把"创建解析器"与"输出结构化结果"打包为一个可注册单元，
 * 供 {@link BatchOcrMain} 调度。
 *
 * <p>每个内置解析器都对应一个 {@code ParserSpec}，集中注册在
 * {@link #REGISTRY} 中。配置项 {@code PARSER_KEY} 即按此 key 查找。
 *
 * <p>由于每个解析器的 Result 类型不同，{@link #writer} 接收 {@code Object}
 * 并在实现里做强制类型转换（保证调度的统一性，运行时 cast 失败概率为 0）。
 */
@Value
@Accessors(fluent = true)
public class ParserSpec {

	/**
	 * 解析器 key（不区分大小写）
	 */
	String key;
	/**
	 * 中文显示名（用于输出抬头）
	 */
	String displayName;
	/**
	 * 默认图片目录（相对于 user.dir）
	 */
	String defaultDir;
	/**
	 * 解析器类 FQN（含包路径），用于输出文件头部给 AI 定位源码
	 */
	String parserClassName;
	/**
	 * 由推理引擎构造一个绑定好 engine 的解析器
	 */
	Function<PPOcrV6Engine, BaseStructuredParser<?>> factory;
	/**
	 * 把结构化结果按既定格式写到 {@link PrintWriter}
	 */
	BiConsumer<Object, PrintWriter> writer;

	// ========================================================================
	// 内置 10 类解析器注册表
	// ========================================================================

	/**
	 * 内置解析器注册表，按 key 的字母序排序。
	 *
	 * <p>顺序选择 LinkedHashMap：保证 {@link Map#keySet()} 顺序稳定，
	 * 打印帮助信息时输出可预测。
	 */
	private static final Map<String, ParserSpec> REGISTRY = buildRegistry();

	/**
	 * 返回只读的解析器注册表（不可修改）。
	 *
	 * @return 不可变的解析器注册表
	 */
	public static Map<String, ParserSpec> registry() {
		return REGISTRY;
	}

	/**
	 * 按 key 查找解析器规格。
	 *
	 * @param key 解析器 key（不区分大小写）
	 * @return 对应的 {@link ParserSpec}
	 * @throws IllegalArgumentException 未找到该 key
	 */
	public static ParserSpec of(String key) {
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("parser key must not be empty");
		}
		ParserSpec spec = REGISTRY.get(key.toLowerCase(Locale.ROOT));
		if (spec == null) {
			throw new IllegalArgumentException(
				"Unknown parser: " + key + " (available: " + REGISTRY.keySet() + ")");
		}
		return spec;
	}

	/**
	 * 构建内置 10 类解析器的注册表。
	 *
	 * <p>每个解析器条目包含：
	 * <ol>
	 *   <li>{@code key} —— CLI 简写（小写）</li>
	 *   <li>{@code displayName} —— 中文名</li>
	 *   <li>{@code defaultDir} —— 仓库内默认图片目录</li>
	 *   <li>{@code parserClassName} —— 解析器类 FQN（用于输出文件头部给 AI 定位源码）</li>
	 *   <li>{@code factory} —— 通过 {@code PPOcrV6Engine} 构造解析器</li>
	 *   <li>{@code writer} —— 按既定格式输出结构化结果</li>
	 * </ol>
	 *
	 * <p>新增解析器只需在这里追加一条；{@link BatchOcrMain} 不需要改动。
	 */
	private static Map<String, ParserSpec> buildRegistry() {
		Map<String, ParserSpec> map = new LinkedHashMap<>();
		// 身份证
		map.put("idcard", new ParserSpec(
			"idcard", "身份证", "test_images/idcard",
			"net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser",
			IdCardParser::new,
			BatchOcrMain.Writers::writeIdCard));
		// 行驶证
		map.put("vehicle", new ParserSpec(
			"vehicle", "行驶证", "test_images/vehicle",
			"net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser",
			VehicleLicenseParser::new,
			BatchOcrMain.Writers::writeVehicle));
		// 驾驶证
		map.put("driver", new ParserSpec(
			"driver", "驾驶证", "test_images/driver",
			"net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser",
			DriverLicenseParser::new,
			BatchOcrMain.Writers::writeDriver));
		// 银行卡
		map.put("bankcard", new ParserSpec(
			"bankcard", "银行卡", "test_images/bankcard",
			"net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser",
			BankCardParser::new,
			BatchOcrMain.Writers::writeBankCard));
		// 营业执照
		map.put("business", new ParserSpec(
			"business", "营业执照", "test_images/business",
			"net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseParser",
			BusinessLicenseParser::new,
			BatchOcrMain.Writers::writeBusiness));
		// 增值税发票：分发器形态，电子版优先 → 20 位号码判别失败回退老版
		map.put("invoice", new ParserSpec(
			"invoice", "发票", "test_images/invoice",
			"net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceParser",
			InvoiceParser::new,
			BatchOcrMain.Writers::writeInvoice));
		// 火车票
		map.put("train", new ParserSpec(
			"train", "火车票", "test_images/train",
			"net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketParser",
			TrainTicketParser::new,
			BatchOcrMain.Writers::writeTrain));
		// 出租车票
		map.put("taxi", new ParserSpec(
			"taxi", "出租车票", "test_images/taxi",
			"net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptParser",
			TaxiReceiptParser::new,
			BatchOcrMain.Writers::writeTaxi));
		// 户口本
		map.put("household", new ParserSpec(
			"household", "户口本", "test_images/household_register",
			"net.dreamlu.mica.ai.ppocr.structured.parser.household.HouseholdRegisterParser",
			HouseholdRegisterParser::new,
			BatchOcrMain.Writers::writeHousehold));
		// 拼多多福袋
		map.put("pdd", new ParserSpec(
			"pdd", "拼多多福袋", "test_images/pdd",
			"net.dreamlu.mica.ai.ppocr.structured.parser.pdd.PddLuckyBagParser",
			PddLuckyBagParser::new,
			BatchOcrMain.Writers::writePdd));
		return Collections.unmodifiableMap(map);
	}
}

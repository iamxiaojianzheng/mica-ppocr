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

package net.dreamlu.mica.ai.ppocr.solon;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;

/**
 * 结构化解析器自动配置（Solon 版）。
 *
 * <p>当 classpath 存在 {@link BaseStructuredParser}（即 {@code mica-ppocr-structured} 在依赖链中）时，
 * 自动注册 6 个内置解析器（行驶证 / 身份证 / 银行卡 / 驾照 / 营业执照 / 发票）和 {@link PPOcrTemplate} 模板。
 *
 * <p>解析器依赖 {@link PPOcrV6Engine} bean 存在；{@link PPOcrTemplate}
 * 封装了 "OCR 推理 + 结构化解析" 一站式调用。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private PPOcrTemplate ppocr;
 *
 * VehicleLicenseResult result = ppocr.vehicleLicense().parse(imageBytes);
 * }</pre>
 */
@Configuration
@Condition(onClass=BaseStructuredParser.class, onExpression = "${mica.ai.ppocr.enabled:true} == true")
public class StructuredParserAutoConfiguration {

	/**
	 * 注册行驶证解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 行驶证解析器
	 */
	@Bean
	@Condition(onMissingBean = VehicleLicenseParser.class)
	public VehicleLicenseParser vehicleLicenseParser(PPOcrV6Engine engine) {
		return new VehicleLicenseParser(engine);
	}

	/**
	 * 注册身份证解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 身份证解析器
	 */
	@Bean
	@Condition(onMissingBean = IdCardParser.class)
	public IdCardParser idCardParser(PPOcrV6Engine engine) {
		return new IdCardParser(engine);
	}

	/**
	 * 注册银行卡解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 银行卡解析器
	 */
	@Bean
	@Condition(onMissingBean = BankCardParser.class)
	public BankCardParser bankCardParser(PPOcrV6Engine engine) {
		return new BankCardParser(engine);
	}

	/**
	 * 注册驾驶证解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 驾驶证解析器
	 */
	@Bean
	@Condition(onMissingBean = DriverLicenseParser.class)
	public DriverLicenseParser driverLicenseParser(PPOcrV6Engine engine) {
		return new DriverLicenseParser(engine);
	}

	/**
	 * 注册营业执照解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 营业执照解析器
	 */
	@Bean
	@Condition(onMissingBean=BusinessLicenseParser.class)
	public BusinessLicenseParser businessLicenseParser(PPOcrV6Engine engine) {
		return new BusinessLicenseParser(engine);
	}

	/**
	 * 注册增值税发票解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 发票解析器
	 */
	@Bean
	@Condition(onMissingBean = InvoiceParser.class)
	public InvoiceParser invoiceParser(PPOcrV6Engine engine) {
		return new InvoiceParser(engine);
	}

	/**
	 * 注册 PP-OCR 结构化识别模板。
	 *
	 * <p>仅当容器中存在 {@link PPOcrV6Engine} 时才创建，避免在未配置模型路径时启动失败。
	 *
	 * @param engine                PP-OCRv6 推理引擎
	 * @param vehicleLicenseParser  行驶证解析器
	 * @param idCardParser          身份证解析器
	 * @param bankCardParser        银行卡解析器
	 * @param driverLicenseParser   驾驶证解析器
	 * @param businessLicenseParser 营业执照解析器
	 * @param invoiceParser         发票解析器
	 * @return 结构化识别模板
	 */
	@Bean
	@Condition(onMissingBean = PPOcrTemplate.class, onBean = PPOcrV6Engine.class)
	public PPOcrTemplate ppocrTemplate(PPOcrV6Engine engine,
									   VehicleLicenseParser vehicleLicenseParser,
									   IdCardParser idCardParser,
									   BankCardParser bankCardParser,
									   DriverLicenseParser driverLicenseParser,
									   BusinessLicenseParser businessLicenseParser,
									   InvoiceParser invoiceParser) {
		return new PPOcrTemplate(engine,
			vehicleLicenseParser,
			idCardParser,
			bankCardParser,
			driverLicenseParser,
			businessLicenseParser,
			invoiceParser);
	}
}
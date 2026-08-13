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

package net.dreamlu.mica.ai.ppocr.autoconfigure;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 结构化解析器自动配置。
 *
 * <p>当 classpath 存在 {@link BaseStructuredParser}（即 {@code mica-ppocr-structured} 在依赖链中）时，
 * 自动注册 5 个内置解析器（行驶证 / 身份证 / 银行卡 / 驾照 / 营业执照）和 {@link PPOcrTemplate} 模板。
 *
 * <p>解析器是无状态单例，可直接注入使用；{@link PPOcrTemplate} 封装了
 * "OCR 推理 + 结构化解析" 一站式调用，依赖 {@link PPOcrV6Engine} bean 存在。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private PPOcrTemplate ppocr;
 *
 * VehicleLicenseResult result = ppocr.parseVehicleLicense(image);
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnClass(BaseStructuredParser.class)
@AutoConfigureAfter(PPOCRAutoConfiguration.class)
public class StructuredParserAutoConfiguration {

	/**
	 * 注册行驶证解析器。
	 *
	 * @return 行驶证解析器单例
	 */
	@Bean
	@ConditionalOnMissingBean
	public VehicleLicenseParser vehicleLicenseParser() {
		return VehicleLicenseParser.INSTANCE;
	}

	/**
	 * 注册身份证解析器。
	 *
	 * @return 身份证解析器单例
	 */
	@Bean
	@ConditionalOnMissingBean
	public IdCardParser idCardParser() {
		return IdCardParser.INSTANCE;
	}

	/**
	 * 注册银行卡解析器。
	 *
	 * @return 银行卡解析器单例
	 */
	@Bean
	@ConditionalOnMissingBean
	public BankCardParser bankCardParser() {
		return BankCardParser.INSTANCE;
	}

	/**
	 * 注册驾驶证解析器。
	 *
	 * @return 驾驶证解析器单例
	 */
	@Bean
	@ConditionalOnMissingBean
	public DriverLicenseParser driverLicenseParser() {
		return DriverLicenseParser.INSTANCE;
	}

	/**
	 * 注册营业执照解析器。
	 *
	 * @return 营业执照解析器单例
	 */
	@Bean
	@ConditionalOnMissingBean
	public BusinessLicenseParser businessLicenseParser() {
		return BusinessLicenseParser.INSTANCE;
	}

	/**
	 * 注册 PP-OCR 结构化识别模板。
	 *
	 * <p>仅当容器中存在 {@link PPOcrV6Engine} 时才创建，避免在未配置模型路径时启动失败。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 结构化识别模板
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(PPOcrV6Engine.class)
	public PPOcrTemplate ppocrTemplate(PPOcrV6Engine engine) {
		return new PPOcrTemplate(engine);
	}
}

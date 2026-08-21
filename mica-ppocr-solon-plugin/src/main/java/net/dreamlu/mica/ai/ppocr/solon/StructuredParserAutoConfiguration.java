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
import net.dreamlu.mica.ai.ppocr.structured.parser.household.HouseholdRegisterParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.AppContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化解析器自动配置（Solon 版）。
 *
 * <p>当 classpath 存在 {@link BaseStructuredParser}（即 {@code mica-ppocr-structured} 在依赖链中）时，
 * 自动注册 9 个内置解析器（行驶证 / 身份证 / 银行卡 / 驾照 / 营业执照 / 发票 / 火车票 / 出租车票 / 户口本）
 * 和 {@link PPOcrTemplate} 模板。
 *
 * <p>每个解析器都是独立 {@code @Bean}，配合 {@code onMissingBean}，
 * 业务方可精确 override 单个解析器实现。
 *
 * <p>注意：组装 {@link PPOcrTemplate} 时通过 {@link org.noear.solon.Solon#context()} 拿到 AppContext
 * 再调用 {@code getBeansOfType(BaseStructuredParser.class)} 手动收集子类，
 * 而非 {@code List<BaseStructuredParser<?>>} 自动注入——
 * Solon 4.x 的参数注入对嵌套 wildcard 泛型解析不稳定，手动收集更可靠。
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
@Condition(onClass = BaseStructuredParser.class, onExpression = "${mica.ai.ppocr.enabled:true} == true")
public class StructuredParserAutoConfiguration {

	@Bean
	@Condition(onMissingBean = VehicleLicenseParser.class)
	public VehicleLicenseParser vehicleLicenseParser(PPOcrV6Engine engine) {
		return new VehicleLicenseParser(engine);
	}

	@Bean
	@Condition(onMissingBean = IdCardParser.class)
	public IdCardParser idCardParser(PPOcrV6Engine engine) {
		return new IdCardParser(engine);
	}

	@Bean
	@Condition(onMissingBean = BankCardParser.class)
	public BankCardParser bankCardParser(PPOcrV6Engine engine) {
		return new BankCardParser(engine);
	}

	@Bean
	@Condition(onMissingBean = DriverLicenseParser.class)
	public DriverLicenseParser driverLicenseParser(PPOcrV6Engine engine) {
		return new DriverLicenseParser(engine);
	}

	@Bean
	@Condition(onMissingBean = BusinessLicenseParser.class)
	public BusinessLicenseParser businessLicenseParser(PPOcrV6Engine engine) {
		return new BusinessLicenseParser(engine);
	}

	@Bean
	@Condition(onMissingBean = InvoiceParser.class)
	public InvoiceParser invoiceParser(PPOcrV6Engine engine) {
		return new InvoiceParser(engine);
	}

	@Bean
	@Condition(onMissingBean = TrainTicketParser.class)
	public TrainTicketParser trainTicketParser(PPOcrV6Engine engine) {
		return new TrainTicketParser(engine);
	}

	@Bean
	@Condition(onMissingBean = TaxiReceiptParser.class)
	public TaxiReceiptParser taxiReceiptParser(PPOcrV6Engine engine) {
		return new TaxiReceiptParser(engine);
	}

	@Bean
	@Condition(onMissingBean = HouseholdRegisterParser.class)
	public HouseholdRegisterParser householdRegisterParser(PPOcrV6Engine engine) {
		return new HouseholdRegisterParser(engine);
	}

	/**
	 * 注册 PP-OCR 结构化识别模板。
	 *
	 * <p>仅当容器中存在 {@link PPOcrV6Engine} 时才创建，避免在未配置模型路径时启动失败。
	 * 通过 {@link Solon#context()} 拿到 AppContext，再调用
	 * {@code getBeansOfType(BaseStructuredParser.class)} 手动收集所有
	 * {@link BaseStructuredParser} 子类，绕开 Solon 4.x 对
	 * {@code List<BaseStructuredParser<?>>} 自动注入的不稳定支持。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 结构化识别模板
	 */
	@Bean
	@Condition(onMissingBean = PPOcrTemplate.class, onBean = PPOcrV6Engine.class)
	public PPOcrTemplate ppocrTemplate(AppContext appContext, PPOcrV6Engine engine) {
		// Solon 4.x 的 getBeansOfType(Class) / getWrapsOfType(Class) 按精确类型匹配，
		// 抽象父类拿不到子类实例；改用 subBeansOfType(Class, Consumer) 按 isAssignableFrom 流式收集。
		List<BaseStructuredParser<?>> parsers = new ArrayList<>();
		appContext.subBeansOfType(BaseStructuredParser.class, parsers::add);
		return new PPOcrTemplate(engine, parsers);
	}
}

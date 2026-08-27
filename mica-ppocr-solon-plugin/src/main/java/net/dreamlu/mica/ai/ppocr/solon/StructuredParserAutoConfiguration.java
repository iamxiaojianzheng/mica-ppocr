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
import net.dreamlu.mica.ai.ppocr.structured.parser.pdd.PddLuckyBagParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.AppContext;

/**
 * 结构化解析器自动配置（Solon 版）。
 *
 * <p>当 classpath 存在 {@link BaseStructuredParser}（即 {@code mica-ppocr-structured} 在依赖链中）时，
 * 自动注册 10 个内置解析器（行驶证 / 身份证 / 银行卡 / 驾照 / 营业执照 / 发票 / 火车票 / 出租车票 / 户口本 / 拼多多福袋）
 * 和 {@link PPOcrTemplate} 模板。
 *
 * <p>每个解析器都是独立 {@code @Bean}，配合 {@code onMissingBean}，
 * 业务方可精确 override 单个解析器实现。
 *
 * <p>注意：{@link PPOcrTemplate} 构造时仅持有 {@link AppContext} 与 engine，不收集解析器；
 * 解析器按需懒加载——首次调用 {@code get(Class)} 时通过
 * {@code AppContext#getBean(Class)} 实时查找并缓存（见 {@link PPOcrTemplate#get(Class)}），
 * 而非 {@code List<BaseStructuredParser<?>>} 自动注入或构造期批量收集——
 * Solon 4.x 的参数注入对嵌套 wildcard 泛型解析不稳定，构造期收集还受 Bean 注册顺序影响。
 *
 * <p>使用示例：
 * <pre>
 * &#64;Autowired
 * private PPOcrTemplate ppocr;
 *
 * VehicleLicenseResult result = ppocr.vehicleLicense().parse(imageBytes);
 * </pre>
 */
@Configuration
@Condition(onClass = BaseStructuredParser.class, onExpression = "${mica.ai.ppocr.enabled:true} == true")
public class StructuredParserAutoConfiguration {

	/**
	 * 注册行驶证解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 行驶证解析器实例
	 */
	@Bean
	@Condition(onMissingBean = VehicleLicenseParser.class)
	public VehicleLicenseParser vehicleLicenseParser(PPOcrV6Engine engine) {
		return new VehicleLicenseParser(engine);
	}

	/**
	 * 注册身份证解析器（正反面自动判定）。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 身份证解析器实例
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
	 * @return 银行卡解析器实例
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
	 * @return 驾驶证解析器实例
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
	 * @return 营业执照解析器实例
	 */
	@Bean
	@Condition(onMissingBean = BusinessLicenseParser.class)
	public BusinessLicenseParser businessLicenseParser(PPOcrV6Engine engine) {
		return new BusinessLicenseParser(engine);
	}

	/**
	 * 注册发票统一入口解析器（分发器：自动判别新版电子发票 / 老版增值税发票）。
	 *
	 * <p>子解析器（{@code VatInvoiceParser} / {@code ElectronicInvoiceParser}）在构造时
	 * 内部初始化，不作为独立 bean 暴露。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 发票分发器实例
	 */
	@Bean
	@Condition(onMissingBean = InvoiceParser.class)
	public InvoiceParser invoiceParser(PPOcrV6Engine engine) {
		return new InvoiceParser(engine);
	}

	/**
	 * 注册火车票解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 火车票解析器实例
	 */
	@Bean
	@Condition(onMissingBean = TrainTicketParser.class)
	public TrainTicketParser trainTicketParser(PPOcrV6Engine engine) {
		return new TrainTicketParser(engine);
	}

	/**
	 * 注册出租车票解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 出租车票解析器实例
	 */
	@Bean
	@Condition(onMissingBean = TaxiReceiptParser.class)
	public TaxiReceiptParser taxiReceiptParser(PPOcrV6Engine engine) {
		return new TaxiReceiptParser(engine);
	}

	/**
	 * 注册户口本解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 户口本解析器实例
	 */
	@Bean
	@Condition(onMissingBean = HouseholdRegisterParser.class)
	public HouseholdRegisterParser householdRegisterParser(PPOcrV6Engine engine) {
		return new HouseholdRegisterParser(engine);
	}

	/**
	 * 注册拼多多福袋解析器。
	 *
	 * @param engine PP-OCRv6 推理引擎
	 * @return 拼多多福袋解析器实例
	 */
	@Bean
	@Condition(onMissingBean = PddLuckyBagParser.class)
	public PddLuckyBagParser pddLuckyBagParser(PPOcrV6Engine engine) {
		return new PddLuckyBagParser(engine);
	}

	/**
	 * 注册 PP-OCR 结构化识别模板。
	 *
	 * <p>仅当容器中存在 {@link PPOcrV6Engine} 时才创建，避免在未配置模型路径时启动失败。
	 * 构造时仅将注入的 {@link AppContext} 与 engine 交给 {@link PPOcrTemplate}，
	 * 不收集任何解析器。
	 *
	 * <p>解析器查找采用懒加载（见 {@link PPOcrTemplate#get(Class)}）：首次调用
	 * {@code get(Class)} 时才通过 {@code AppContext#getBean(Class)} 实时查找并缓存，
	 * 绕开 Solon 4.x 对 {@code List<BaseStructuredParser<?>>} 自动注入的不稳定支持，
	 * 也避免 {@code @Configuration} 内 {@code @Bean} 注册顺序未定导致的非确定结果。
	 *
	 * @param context 应用程序上下文
	 * @param engine  PP-OCRv6 推理引擎
	 * @return 结构化识别模板
	 */
	@Bean
	@Condition(onMissingBean = PPOcrTemplate.class, onBean = PPOcrV6Engine.class)
	public PPOcrTemplate ppocrTemplate(AppContext context, PPOcrV6Engine engine) {
		return new PPOcrTemplate(context, engine);
	}
}

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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;

import java.util.List;

/**
 * 发票统一入口解析器（分发器）：自动判别新版电子发票 / 老版增值税发票。
 *
 * <p>继承 {@link BaseStructuredParser}，5 个 {@code parse(...)} 一站式重载
 * （String / File / Path / byte[] / InputStream）复用基类 final 实现，
 * 内部走 engine 推理后转 {@link #parseResults(List)}。
 *
 * <p>分发策略（电子版优先，后面新版多）：
 * <ol>
 *   <li>先调 {@link ElectronicInvoiceParser}：以"发票号码固定 20 位"判别
 *       （国家税务总局公告 2024 年第 11 号），命中 → 解析电子发票字段；</li>
 *   <li>判别失败（返回 null）→ 回退 {@link VatInvoiceParser} 解析老版字段；</li>
 *   <li>最终统一标注 {@link InvoiceVersion}（ELECTRONIC / VAT）。</li>
 * </ol>
 *
 * <p>{@link VatInvoiceParser} / {@link ElectronicInvoiceParser} 为内部实现细节，
 * 在构造时直接 new，不作为 Spring/Solon bean 暴露——调用方无感知。
 * 调用方无需关心传入的是新版还是老版发票，统一走本类入口即可。
 */
public class InvoiceParser extends BaseStructuredParser<InvoiceResult> {
	private final VatInvoiceParser vatParser;
	private final ElectronicInvoiceParser electronicParser;

	/**
	 * 构造发票分发器。
	 *
	 * @param engine 推理引擎；可为 null（仅当只调用 {@code parseResults} 时）
	 */
	public InvoiceParser(PPOcrV6Engine engine) {
		super(engine);
		this.vatParser = new VatInvoiceParser();
		this.electronicParser = new ElectronicInvoiceParser();
	}

	@Override
	public InvoiceResult parseResults(List<PPOcrV6Result> results) {
		InvoiceResult r = electronicParser.parseResults(results);
		if (r == null) {
			r = vatParser.parseResults(results);
			r.setVersion(InvoiceVersion.VAT);
		} else {
			r.setVersion(InvoiceVersion.ELECTRONIC);
		}
		return r;
	}
}

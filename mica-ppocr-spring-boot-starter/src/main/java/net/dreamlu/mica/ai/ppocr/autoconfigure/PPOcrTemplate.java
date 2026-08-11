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
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseStructuredParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseResult;
import org.opencv.core.Mat;

import java.util.List;

/**
 * PP-OCR 结构化识别模板，封装 "OCR 推理 + 结构化解析" 一站式调用。
 *
 * <p>持有 {@link PPOcrV6Engine} 实例，提供：
 * <ul>
 *   <li>{@link #run(Mat)} —— 纯 OCR 识别，返回散落文字框列表；</li>
 *   <li>{@link #parse(Mat, BaseStructuredParser)} —— 通用结构化解析，传入任意解析器；</li>
 *   <li>{@link #parseVehicleLicense(Mat)} / {@link #parseIdCard(Mat)} /
 *       {@link #parseBankCard(Mat)} / {@link #parseDriverLicense(Mat)} ——
 *       内置解析器便捷方法，一行调用完成 "检测 → 识别 → 结构化"。</li>
 * </ul>
 *
 * <p>典型用法（Spring 注入）：
 * <pre>{@code
 * @Autowired
 * private PPOcrTemplate ppocr;
 *
 * public VehicleLicenseResult recognize(Mat image) {
 *     return ppocr.parseVehicleLicense(image);
 * }
 * }</pre>
 *
 * <p>典型用法（非 Spring，工具类风格）：
 * <pre>{@code
 * try (var engine = new PPOcrV6Engine(config)) {
 *     var template = new PPOcrTemplate(engine);
 *     IdCardResult idCard = template.parseIdCard(image);
 * }
 * }</pre>
 *
 * <p>本类不接管 {@link PPOcrV6Engine} 的生命周期：
 * Spring 场景下由容器管理 engine 的关闭；非 Spring 场景由调用方自行关闭 engine。
 */
public final class PPOcrTemplate {
	private final PPOcrV6Engine engine;

	/**
	 * 构造模板，传入已初始化的推理引擎。
	 *
	 * @param engine PP-OCRv6 推理引擎（不为 null）
	 */
	public PPOcrTemplate(PPOcrV6Engine engine) {
		if (engine == null) {
			throw new IllegalArgumentException("PPOcrV6Engine must not be null");
		}
		this.engine = engine;
	}

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * @param imgBgr BGR 格式图像 (H, W, 3) uint8
	 * @return 识别结果列表（按阅读顺序排列）
	 */
	public List<PPOcrV6Result> run(Mat imgBgr) {
		return engine.run(imgBgr);
	}

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * @param imgBgr BGR 格式图像
	 * @param parser 结构化解析器（行驶证 / 身份证 / 银行卡 / 驾照 或自定义解析器）
	 * @param <R>    业务结果类型
	 * @return 结构化结果
	 */
	public <R> R parse(Mat imgBgr, BaseStructuredParser<R> parser) {
		return parser.parseResults(engine.run(imgBgr));
	}

	/**
	 * 行驶证结构化识别。
	 *
	 * @param imgBgr 行驶证图片（BGR）
	 * @return 行驶证解析结果
	 */
	public VehicleLicenseResult parseVehicleLicense(Mat imgBgr) {
		return parse(imgBgr, VehicleLicenseParser.INSTANCE);
	}

	/**
	 * 身份证结构化识别（正反面自动判定）。
	 *
	 * @param imgBgr 身份证图片（BGR）
	 * @return 身份证解析结果
	 */
	public IdCardResult parseIdCard(Mat imgBgr) {
		return parse(imgBgr, IdCardParser.INSTANCE);
	}

	/**
	 * 银行卡结构化识别。
	 *
	 * @param imgBgr 银行卡图片（BGR）
	 * @return 银行卡解析结果
	 */
	public BankCardResult parseBankCard(Mat imgBgr) {
		return parse(imgBgr, BankCardParser.INSTANCE);
	}

	/**
	 * 驾驶证结构化识别。
	 *
	 * @param imgBgr 驾驶证图片（BGR）
	 * @return 驾驶证解析结果
	 */
	public DriverLicenseResult parseDriverLicense(Mat imgBgr) {
		return parse(imgBgr, DriverLicenseParser.INSTANCE);
	}
}

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
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketParser;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;

import java.io.File;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

/**
 * PP-OCR 结构化识别模板（Solon 插件版）。
 *
 * <p>持有 {@link PPOcrV6Engine} 与 8 个内置结构化解析器实例，对外提供：
 * <ul>
 *   <li>{@link #run(String)} / {@link #run(File)} / {@link #run(Path)} / {@link #run(byte[])} /
 *       {@link #run(InputStream)} —— 纯 OCR 识别，返回散落文字框列表；</li>
 *   <li>{@link #vehicleLicense()} / {@link #idCard()} / {@link #bankCard()} /
 *       {@link #driverLicense()} / {@link #businessLicense()} / {@link #invoice()} /
 *       {@link #trainTicket()} / {@link #taxiReceipt()} ——
 *       获取 8 类内置解析器，每个解析器已绑定 engine，自带 5 种入参的 {@code parse(...)} 重载。</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 * @Autowired
 * private PPOcrTemplate ppocr;
 *
 * @PostMapping("/vehicle")
 * public VehicleLicenseResult recognize(@RequestParam("file") MultipartFile file) throws IOException {
 *     return ppocr.vehicleLicense().parse(file.getBytes());
 * }
 * }</pre>
 *
 * <p>本类不接管 {@link PPOcrV6Engine} 的生命周期：
 * Solon 场景下由容器管理 engine 的关闭；非容器场景由调用方自行关闭 engine。
 */
public final class PPOcrTemplate {

	private final PPOcrV6Engine engine;
	private final VehicleLicenseParser vehicleLicenseParser;
	private final IdCardParser idCardParser;
	private final BankCardParser bankCardParser;
	private final DriverLicenseParser driverLicenseParser;
	private final BusinessLicenseParser businessLicenseParser;
	private final InvoiceParser invoiceParser;
	private final TrainTicketParser trainTicketParser;
	private final TaxiReceiptParser taxiReceiptParser;

	/**
	 * 构造模板，传入已初始化的推理引擎与 8 个结构化解析器实例。
	 *
	 * @param engine                  PP-OCRv6 推理引擎（不为 null）
	 * @param vehicleLicenseParser    行驶证解析器（不为 null）
	 * @param idCardParser            身份证解析器（不为 null）
	 * @param bankCardParser          银行卡解析器（不为 null）
	 * @param driverLicenseParser     驾驶证解析器（不为 null）
	 * @param businessLicenseParser   营业执照解析器（不为 null）
	 * @param invoiceParser           发票解析器（不为 null）
	 * @param trainTicketParser       火车票解析器（不为 null）
	 * @param taxiReceiptParser       出租车票解析器（不为 null）
	 */
	public PPOcrTemplate(PPOcrV6Engine engine,
						 VehicleLicenseParser vehicleLicenseParser,
						 IdCardParser idCardParser,
						 BankCardParser bankCardParser,
						 DriverLicenseParser driverLicenseParser,
						 BusinessLicenseParser businessLicenseParser,
						 InvoiceParser invoiceParser,
						 TrainTicketParser trainTicketParser,
						 TaxiReceiptParser taxiReceiptParser) {
		if (engine == null) {
			throw new IllegalArgumentException("PPOcrV6Engine must not be null");
		}
		if (vehicleLicenseParser == null) {
			throw new IllegalArgumentException("VehicleLicenseParser must not be null");
		}
		if (idCardParser == null) {
			throw new IllegalArgumentException("IdCardParser must not be null");
		}
		if (bankCardParser == null) {
			throw new IllegalArgumentException("BankCardParser must not be null");
		}
		if (driverLicenseParser == null) {
			throw new IllegalArgumentException("DriverLicenseParser must not be null");
		}
		if (businessLicenseParser == null) {
			throw new IllegalArgumentException("BusinessLicenseParser must not be null");
		}
		if (invoiceParser == null) {
			throw new IllegalArgumentException("InvoiceParser must not be null");
		}
		if (trainTicketParser == null) {
			throw new IllegalArgumentException("TrainTicketParser must not be null");
		}
		if (taxiReceiptParser == null) {
			throw new IllegalArgumentException("TaxiReceiptParser must not be null");
		}
		this.engine = engine;
		this.vehicleLicenseParser = vehicleLicenseParser;
		this.idCardParser = idCardParser;
		this.bankCardParser = bankCardParser;
		this.driverLicenseParser = driverLicenseParser;
		this.businessLicenseParser = businessLicenseParser;
		this.invoiceParser = invoiceParser;
		this.trainTicketParser = trainTicketParser;
		this.taxiReceiptParser = taxiReceiptParser;
	}

	// ==================================================================
	// 纯 OCR（非结构化）：返回散落文字框列表
	// ==================================================================

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * @param imagePath 图片路径
	 * @return 识别结果列表（按阅读顺序排列）
	 */
	public List<PPOcrV6Result> run(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return run(Path.of(imagePath));
	}

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * @param imageFile 图片文件
	 * @return 识别结果列表（按阅读顺序排列）
	 */
	public List<PPOcrV6Result> run(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return run(imageFile.toPath());
	}

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>兼容非默认文件系统（如 ZIP / JIMFS / 内存 FS）：优先走 native 文件读取，
	 * 不支持的 FileSystem 自动退回 {@code Files.readAllBytes}。
	 *
	 * @param imagePath 图片路径
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws UncheckedIOException 读取字节时发生 IO 异常
	 */
	public List<PPOcrV6Result> run(Path imagePath) {
		return engine.run(imagePath);
	}

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * @param imgBytes 图片字节
	 * @return 识别结果列表（按阅读顺序排列）
	 */
	public List<PPOcrV6Result> run(byte[] imgBytes) {
		return engine.run(imgBytes);
	}

	/**
	 * 纯 OCR 识别：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>内部读取全部流为 byte[] 后调用 {@code engine.run(byte[])}。
	 * 流由调用方负责关闭（{@code InputStream.readAllBytes()} 会读到 EOF 但不 close）。
	 *
	 * @param in 图片输入流
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws java.io.IOException 读取流失败
	 */
	public List<PPOcrV6Result> run(InputStream in) throws java.io.IOException {
		if (in == null) {
			throw new IllegalArgumentException("InputStream must not be null");
		}
		return engine.run(in.readAllBytes());
	}

	// ==================================================================
	// 结构化解析器：每个解析器自带 5 种入参的 parse(...) 重载
	// ==================================================================

	/**
	 * 获取行驶证结构化解析器。
	 *
	 * @return 行驶证解析器实例（已绑定当前 engine）
	 */
	public VehicleLicenseParser vehicleLicense() {
		return vehicleLicenseParser;
	}

	/**
	 * 获取身份证结构化解析器（正反面自动判定）。
	 *
	 * @return 身份证解析器实例（已绑定当前 engine）
	 */
	public IdCardParser idCard() {
		return idCardParser;
	}

	/**
	 * 获取银行卡结构化解析器。
	 *
	 * @return 银行卡解析器实例（已绑定当前 engine）
	 */
	public BankCardParser bankCard() {
		return bankCardParser;
	}

	/**
	 * 获取驾驶证结构化解析器。
	 *
	 * @return 驾驶证解析器实例（已绑定当前 engine）
	 */
	public DriverLicenseParser driverLicense() {
		return driverLicenseParser;
	}

	/**
	 * 获取营业执照结构化解析器。
	 *
	 * @return 营业执照解析器实例（已绑定当前 engine）
	 */
	public BusinessLicenseParser businessLicense() {
		return businessLicenseParser;
	}

	/**
	 * 获取增值税发票结构化解析器。
	 *
	 * @return 发票解析器实例（已绑定当前 engine）
	 */
	public InvoiceParser invoice() {
		return invoiceParser;
	}

	/**
	 * 获取火车票结构化解析器。
	 *
	 * @return 火车票解析器实例（已绑定当前 engine）
	 */
	public TrainTicketParser trainTicket() {
		return trainTicketParser;
	}

	/**
	 * 获取出租车票结构化解析器。
	 *
	 * @return 出租车票解析器实例（已绑定当前 engine）
	 */
	public TaxiReceiptParser taxiReceipt() {
		return taxiReceiptParser;
	}
}
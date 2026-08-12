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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PP-OCR 结构化识别模板，封装 "OCR 推理 + 结构化解析" 一站式调用。
 *
 * <p>持有 {@link PPOcrV6Engine} 实例，提供：
 * <ul>
 *   <li>{@link #run(String)} / {@link #run(File)} / {@link #run(Path)} / {@link #run(byte[])} /
 *       {@link #run(InputStream)} —— 纯 OCR 识别，返回散落文字框列表；</li>
 *   <li>{@link #parse(String, BaseStructuredParser)} / {@link #parse(File, BaseStructuredParser)} /
 *       {@link #parse(Path, BaseStructuredParser)} / {@link #parse(byte[], BaseStructuredParser)} /
 *       {@link #parse(InputStream, BaseStructuredParser)} —— 通用结构化解析，传入任意解析器；</li>
 *   <li>{@link #parseVehicleLicense(String)} 等 4 类证件便捷方法（5 种入参各一）
 *       —— 一行调用完成 "检测 → 识别 → 结构化"。</li>
 * </ul>
 *
 * <p>所有方法共 5 种入参重载（每种方法都提供）：
 * <ul>
 *   <li>{@code String} —— 图片文件路径（PNG / JPG / BMP 等任意 OpenCV 支持的格式）</li>
 *   <li>{@link File} —— 图片文件对象</li>
 *   <li>{@link Path} —— 图片路径（兼容非默认文件系统，如 ZIP / JIMFS）</li>
 *   <li>{@code byte[]} —— 图片字节（典型场景：Spring Boot 上传 {@code MultipartFile.getBytes()}）</li>
 *   <li>{@link InputStream} —— 图片输入流（典型场景：URL 资源 / S3 下载流）</li>
 * </ul>
 *
 * <p>内部自动处理 OpenCV Mat 解码与 release，调用方<strong>无需关心 native 内存管理</strong>。
 *
 * <p>典型用法（Spring Boot 上传）：
 * <pre>{@code
 * @Autowired
 * private PPOcrTemplate ppocr;
 *
 * @PostMapping("/vehicle")
 * public VehicleLicenseResult recognize(@RequestParam("file") MultipartFile file) throws IOException {
 *     return ppocr.parseVehicleLicense(file.getBytes());
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
	 * <p>内部读取全部流为 byte[] 后调用 engine.run(byte[])。
	 * 流由调用方负责关闭（不强制关闭，InputStream.readAllBytes() 会读到 EOF 但不 close）。
	 *
	 * @param in 图片输入流
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws IOException 读取流失败
	 */
	public List<PPOcrV6Result> run(InputStream in) throws IOException {
		return engine.run(toBytes(in));
	}

	// ==================================================================
	// 通用结构化解析：传入自定义解析器
	// ==================================================================

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * @param imagePath 图片路径
	 * @param parser    结构化解析器
	 * @param <R>       业务结果类型
	 * @return 结构化结果
	 */
	public <R> R parse(String imagePath, BaseStructuredParser<R> parser) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return parse(Path.of(imagePath), parser);
	}

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * @param imageFile 图片文件
	 * @param parser    结构化解析器
	 * @param <R>       业务结果类型
	 * @return 结构化结果
	 */
	public <R> R parse(File imageFile, BaseStructuredParser<R> parser) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return parse(imageFile.toPath(), parser);
	}

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * <p>兼容非默认文件系统（如 ZIP / JIMFS / 内存 FS）。
	 *
	 * @param imagePath 图片路径
	 * @param parser    结构化解析器
	 * @param <R>       业务结果类型
	 * @return 结构化结果
	 * @throws UncheckedIOException 读取字节时发生 IO 异常
	 */
	public <R> R parse(Path imagePath, BaseStructuredParser<R> parser) {
		return parser.parseResults(engine.run(imagePath));
	}

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * <p>典型场景：Spring Boot 上传 {@code MultipartFile.getBytes()}。
	 *
	 * @param imgBytes 图片字节
	 * @param parser   结构化解析器
	 * @param <R>      业务结果类型
	 * @return 结构化结果
	 */
	public <R> R parse(byte[] imgBytes, BaseStructuredParser<R> parser) {
		return parser.parseResults(engine.run(imgBytes));
	}

	/**
	 * 通用结构化解析：先 OCR 识别，再交由指定解析器组织成业务字段。
	 *
	 * @param in     图片输入流
	 * @param parser 结构化解析器
	 * @param <R>    业务结果类型
	 * @return 结构化结果
	 * @throws IOException 读取流失败
	 */
	public <R> R parse(InputStream in, BaseStructuredParser<R> parser) throws IOException {
		return parser.parseResults(engine.run(toBytes(in)));
	}

	// ==================================================================
	// 行驶证
	// ==================================================================

	/**
	 * 行驶证结构化解析（图片路径）。
	 *
	 * @param imagePath 图片文件路径
	 * @return 行驶证结构化结果（含 rawResults + fieldBoxes）
	 * @throws IllegalArgumentException 图片路径为空
	 */
	public VehicleLicenseResult parseVehicleLicense(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return parseVehicleLicense(Path.of(imagePath));
	}

	/**
	 * 行驶证结构化解析（File）。
	 *
	 * @param imageFile 图片文件
	 * @return 行驶证结构化结果
	 * @throws IllegalArgumentException 文件为 null
	 */
	public VehicleLicenseResult parseVehicleLicense(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return parseVehicleLicense(imageFile.toPath());
	}

	/**
	 * 行驶证结构化解析（Path）。
	 *
	 * @param imagePath 图片路径（兼容非默认文件系统）
	 * @return 行驶证结构化结果
	 */
	public VehicleLicenseResult parseVehicleLicense(Path imagePath) {
		return parse(imagePath, VehicleLicenseParser.INSTANCE);
	}

	/**
	 * 行驶证结构化解析（图片字节）。
	 *
	 * @param imgBytes 图片字节（PNG/JPG 等）
	 * @return 行驶证结构化结果
	 */
	public VehicleLicenseResult parseVehicleLicense(byte[] imgBytes) {
		return parse(imgBytes, VehicleLicenseParser.INSTANCE);
	}

	/**
	 * 行驶证结构化解析（输入流）。
	 *
	 * @param in 图片输入流（自动 readAllBytes）
	 * @return 行驶证结构化结果
	 * @throws IOException 读取流失败
	 */
	public VehicleLicenseResult parseVehicleLicense(InputStream in) throws IOException {
		return parse(in, VehicleLicenseParser.INSTANCE);
	}

	// ==================================================================
	// 身份证（正反面自动判定）
	// ==================================================================

	/**
	 * 身份证结构化解析（图片路径，正反面自动判定）。
	 *
	 * @param imagePath 图片文件路径
	 * @return 身份证结构化结果（含 side 字段标识正反面）
	 * @throws IllegalArgumentException 图片路径为空
	 */
	public IdCardResult parseIdCard(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return parseIdCard(Path.of(imagePath));
	}

	/**
	 * 身份证结构化解析（File）。
	 *
	 * @param imageFile 图片文件
	 * @return 身份证结构化结果
	 * @throws IllegalArgumentException 文件为 null
	 */
	public IdCardResult parseIdCard(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return parseIdCard(imageFile.toPath());
	}

	/**
	 * 身份证结构化解析（Path）。
	 *
	 * @param imagePath 图片路径
	 * @return 身份证结构化结果
	 */
	public IdCardResult parseIdCard(Path imagePath) {
		return parse(imagePath, IdCardParser.INSTANCE);
	}

	/**
	 * 身份证结构化解析（图片字节）。
	 *
	 * @param imgBytes 图片字节
	 * @return 身份证结构化结果
	 */
	public IdCardResult parseIdCard(byte[] imgBytes) {
		return parse(imgBytes, IdCardParser.INSTANCE);
	}

	/**
	 * 身份证结构化解析（输入流）。
	 *
	 * @param in 图片输入流
	 * @return 身份证结构化结果
	 * @throws IOException 读取流失败
	 */
	public IdCardResult parseIdCard(InputStream in) throws IOException {
		return parse(in, IdCardParser.INSTANCE);
	}

	// ==================================================================
	// 银行卡
	// ==================================================================

	/**
	 * 银行卡结构化解析（图片路径）。
	 *
	 * @param imagePath 图片文件路径
	 * @return 银行卡结构化结果
	 * @throws IllegalArgumentException 图片路径为空
	 */
	public BankCardResult parseBankCard(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return parseBankCard(Path.of(imagePath));
	}

	/**
	 * 银行卡结构化解析（File）。
	 *
	 * @param imageFile 图片文件
	 * @return 银行卡结构化结果
	 * @throws IllegalArgumentException 文件为 null
	 */
	public BankCardResult parseBankCard(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return parseBankCard(imageFile.toPath());
	}

	/**
	 * 银行卡结构化解析（Path）。
	 *
	 * @param imagePath 图片路径
	 * @return 银行卡结构化结果
	 */
	public BankCardResult parseBankCard(Path imagePath) {
		return parse(imagePath, BankCardParser.INSTANCE);
	}

	/**
	 * 银行卡结构化解析（图片字节）。
	 *
	 * @param imgBytes 图片字节
	 * @return 银行卡结构化结果
	 */
	public BankCardResult parseBankCard(byte[] imgBytes) {
		return parse(imgBytes, BankCardParser.INSTANCE);
	}

	/**
	 * 银行卡结构化解析（输入流）。
	 *
	 * @param in 图片输入流
	 * @return 银行卡结构化结果
	 * @throws IOException 读取流失败
	 */
	public BankCardResult parseBankCard(InputStream in) throws IOException {
		return parse(in, BankCardParser.INSTANCE);
	}

	// ==================================================================
	// 驾驶证
	// ==================================================================

	/**
	 * 驾驶证结构化解析（图片路径）。
	 *
	 * @param imagePath 图片文件路径
	 * @return 驾驶证结构化结果
	 * @throws IllegalArgumentException 图片路径为空
	 */
	public DriverLicenseResult parseDriverLicense(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return parseDriverLicense(Path.of(imagePath));
	}

	/**
	 * 驾驶证结构化解析（File）。
	 *
	 * @param imageFile 图片文件
	 * @return 驾驶证结构化结果
	 * @throws IllegalArgumentException 文件为 null
	 */
	public DriverLicenseResult parseDriverLicense(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return parseDriverLicense(imageFile.toPath());
	}

	/**
	 * 驾驶证结构化解析（Path）。
	 *
	 * @param imagePath 图片路径
	 * @return 驾驶证结构化结果
	 */
	public DriverLicenseResult parseDriverLicense(Path imagePath) {
		return parse(imagePath, DriverLicenseParser.INSTANCE);
	}

	/**
	 * 驾驶证结构化解析（图片字节）。
	 *
	 * @param imgBytes 图片字节
	 * @return 驾驶证结构化结果
	 */
	public DriverLicenseResult parseDriverLicense(byte[] imgBytes) {
		return parse(imgBytes, DriverLicenseParser.INSTANCE);
	}

	/**
	 * 驾驶证结构化解析（输入流）。
	 *
	 * @param in 图片输入流
	 * @return 驾驶证结构化结果
	 * @throws IOException 读取流失败
	 */
	public DriverLicenseResult parseDriverLicense(InputStream in) throws IOException {
		return parse(in, DriverLicenseParser.INSTANCE);
	}

	// ==================================================================
	// 内部工具
	// ==================================================================

	private static byte[] toBytes(InputStream in) throws IOException {
		if (in == null) {
			throw new IllegalArgumentException("InputStream must not be null");
		}
		return in.readAllBytes();
	}
}

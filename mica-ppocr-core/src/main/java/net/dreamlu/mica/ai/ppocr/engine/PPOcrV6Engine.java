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

package net.dreamlu.mica.ai.ppocr.engine;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import net.dreamlu.mica.ai.ppocr.postprocessor.CtcLabelDecoder;
import net.dreamlu.mica.ai.ppocr.postprocessor.DbPostProcessor;
import net.dreamlu.mica.ai.ppocr.preprocessor.DetectionPreprocessor;
import net.dreamlu.mica.ai.ppocr.preprocessor.RecognitionPreprocessor;
import net.dreamlu.mica.ai.ppocr.utils.BoxUtil;
import net.dreamlu.mica.ai.ppocr.utils.CropUtil;
import net.dreamlu.mica.ai.ppocr.utils.NdArrayUtils;
import net.dreamlu.mica.ai.ppocr.utils.OrtProviders;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/**
 * PP-OCRv6 纯 ONNX Runtime 推理引擎。
 *
 * <p>典型用法：
 * <pre>{@code
 * var config = PPOcrV6Config.builder()
 *     .detModelPath("det.onnx")
 *     .recModelPath("rec.onnx")
 *     .recCharDictPath("dict.txt")
 *     .build();
 * try (var engine = new PPOcrV6Engine(config)) {
 *     // 推荐：直接传文件路径 / byte[]，内部自动处理 native 内存释放
 *     List<PPOcrV6Result> results = engine.run("test_images/vehicle/vehicle1.png");
 * }
 * }</pre>
 *
 * <p>公开 API 只暴露 {@code byte[]} / {@link File} / {@link String} 三种入参，
 * 内部自动解码为 BGR Mat 并在方法返回时 release，调用方无需管理 native 内存。
 * 如确需复用已加载的 Mat，可使用 {@link #runMat(Mat)} 等方法。
 */
@Slf4j
public final class PPOcrV6Engine implements Closeable {
	private final OrtEnvironment env;
	private final OrtSession detSession;
	private final OrtSession recSession;
	private final String detInputName;
	private final String recInputName;

	private final DetectionPreprocessor detPre;
	private final DbPostProcessor detPost;
	private final RecognitionPreprocessor recPre;
	private final CtcLabelDecoder recPost;
	private final int recBatchSize;

	private boolean closed = false;

	/**
	 * 创建 PP-OCRv6 推理引擎。
	 *
	 * @param config 配置参数
	 */
	public PPOcrV6Engine(PPOcrV6Config config) {
		requireFile(config.getDetModelPath(), "detModelPath");
		requireFile(config.getRecModelPath(), "recModelPath");
		requireFile(config.getRecCharDictPath(), "recCharDictPath");
		if (config.getRecBatchSize() < 1) {
			throw new IllegalArgumentException("recBatchSize must be >= 1, got " + config.getRecBatchSize());
		}
		if (config.getRecImageShape() == null || config.getRecImageShape().length != 3) {
			throw new IllegalArgumentException("recImageShape must be [C, H, W]");
		}
		String[] providers = OrtProviders.resolve(!config.isPreferAccelerator());
		log.info("ONNX Runtime provider: {}", String.join(",", providers));
		this.env = OrtEnvironment.getEnvironment();

		try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
			try {
				opts.setIntraOpNumThreads(Math.max(1, config.getIntraOpNumThreads()));
				opts.setInterOpNumThreads(Math.max(1, config.getInterOpNumThreads()));
			} catch (OrtException e) {
				log.warn("设置线程数失败，使用默认值: {}", e.getMessage());
			}

			try {
				this.detSession = env.createSession(config.getDetModelPath(), opts);
				this.recSession = env.createSession(config.getRecModelPath(), opts);
			} catch (OrtException e) {
				close();
				throw new RuntimeException("创建 ONNX Runtime 会话失败: " + e.getMessage(), e);
			}
		}

		try {
			this.detInputName = detSession.getInputNames().iterator().next();
			this.recInputName = recSession.getInputNames().iterator().next();
			this.detPre = new DetectionPreprocessor(config.getDetLimitSideLen(), config.getDetLimitType(), config.getDetMaxSideLimit());
			this.detPost = new DbPostProcessor(config.getDetThresh(), config.getDetBoxThresh(), config.getDetUnclipRatio(),
				1000, 3);
			this.recPre = new RecognitionPreprocessor(config.getRecImageShape()[1], 320, 3200);
			this.recPost = new CtcLabelDecoder(config.getRecCharDictPath());
			this.recBatchSize = config.getRecBatchSize();
		} catch (RuntimeException e) {
			closeOnInitFailure(e);
			throw e;
		}

		log.info("PPOcrV6Engine 初始化完成: det={}, rec={}, vocab={}",
			this.detPre, this.recPre, this.recPost.vocabSize());
	}

	private static void requireFile(String path, String name) {
		if (path == null) {
			throw new IllegalArgumentException(name + " is null");
		}
		if (!Files.isRegularFile(Path.of(path))) {
			throw new IllegalArgumentException(name + ": file not found: " + path);
		}
	}

	private void closeOnInitFailure(Exception cause) {
		closeSessions(cause::addSuppressed);
		closed = true;
	}

	@Override
	public void close() {
		if (!closed) {
			closeSessions(e -> log.debug("关闭 session 失败: {}", e.getMessage()));
			closed = true;
			log.info("PPOcrV6Engine 已关闭");
		}
	}

	private void closeSessions(Consumer<OrtException> onError) {
		if (detSession != null) {
			try {
				detSession.close();
			} catch (OrtException e) {
				onError.accept(e);
			}
		}
		if (recSession != null) {
			try {
				recSession.close();
			} catch (OrtException e) {
				onError.accept(e);
			}
		}
	}

	private void requireOpen() {
		if (closed) {
			throw new IllegalStateException("PPOcrV6Engine has been closed and can no longer be used.");
		}
	}

	@Override
	public String toString() {
		return "PPOcrV6Engine(det=" + detPre + ", rec=" + recPre
			+ ", vocab=" + recPost.vocabSize() + ", closed=" + closed + ")";
	}

	// ==================================================================
	// 推荐公开 API：byte[] / File / String，内部自动管理 Mat 生命周期
	// ==================================================================

	/**
	 * 完整 OCR 流程：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>内部自动解码为 BGR Mat 并在方法返回时 release，调用方无需管理 native 内存。
	 *
	 * @param imagePath 图片路径（PNG / JPG / BMP 等任意 OpenCV 支持的格式）
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws IllegalArgumentException 路径为空、文件不存在或解码失败
	 */
	public List<PPOcrV6Result> run(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return run(Path.of(imagePath));
	}

	/**
	 * 完整 OCR 流程：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>内部自动解码为 BGR Mat 并在方法返回时 release，调用方无需管理 native 内存。
	 *
	 * @param imageFile 图片文件
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws IllegalArgumentException 文件不存在或解码失败
	 */
	public List<PPOcrV6Result> run(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return run(imageFile.toPath());
	}

	/**
	 * 完整 OCR 流程：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>兼容非默认文件系统的 {@link Path}（如 ZIP/JIMFS 等）：优先走 native 文件读取，
	 * 不支持的 FileSystem 自动退回 {@code Files.readAllBytes}。
	 *
	 * @param imagePath 图片路径
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws UncheckedIOException 读取字节时发生 IO 异常
	 */
	public List<PPOcrV6Result> run(Path imagePath) {
		if (imagePath == null) {
			throw new IllegalArgumentException("imagePath must not be null");
		}
		try {
			// 默认 FileSystem → native 读取 OpenCV（省内存，不经过 JVM heap 中转）
			Mat mat = Imgcodecs.imread(imagePath.toFile().getAbsolutePath());
			if (mat.empty()) {
				mat.release();
				throw new IllegalArgumentException("Failed to load image: " + imagePath);
			}
			try {
				return runMat(mat);
			} finally {
				mat.release();
			}
		} catch (UnsupportedOperationException ignore) {
			// 非默认 FileSystem（ZIP / JIMFS / 内存 FS 等）：退回字节流
			try {
				return run(Files.readAllBytes(imagePath));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * 完整 OCR 流程：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>内部自动解码为 BGR Mat 并在方法返回时 release，调用方无需管理 native 内存。
	 * 典型场景：Spring Boot 上传 {@code MultipartFile.getBytes()}。
	 *
	 * @param imgBytes 图片字节（PNG / JPG / BMP 等任意 OpenCV 支持的格式）
	 * @return 识别结果列表（按阅读顺序排列）
	 * @throws IllegalArgumentException 字节为空或解码失败
	 */
	public List<PPOcrV6Result> run(byte[] imgBytes) {
		Mat mat = decodeMat(imgBytes);
		try {
			return runMat(mat);
		} finally {
			mat.release();
		}
	}

	/**
	 * 文本检测（仅检测，不识别）。
	 *
	 * <p>内部自动解码为 BGR Mat 并在方法返回时 release。
	 *
	 * @param imagePath 图片路径
	 * @return boxes 形状 (N, 4, 2) int，scores 长度 N
	 */
	public DetectResult detect(String imagePath) {
		if (imagePath == null || imagePath.isEmpty()) {
			throw new IllegalArgumentException("imagePath must not be empty");
		}
		return detect(Path.of(imagePath));
	}

	/**
	 * 文本检测（仅检测，不识别）。
	 *
	 * @param imageFile 图片文件
	 * @return boxes 形状 (N, 4, 2) int，scores 长度 N
	 */
	public DetectResult detect(File imageFile) {
		if (imageFile == null) {
			throw new IllegalArgumentException("imageFile must not be null");
		}
		return detect(imageFile.toPath());
	}

	/**
	 * 文本检测（仅检测，不识别）。
	 *
	 * <p>兼容非默认文件系统的 {@link Path}（如 ZIP/JIMFS 等）。
	 *
	 * @param imagePath 图片路径
	 * @return boxes 形状 (N, 4, 2) int，scores 长度 N
	 * @throws UncheckedIOException 读取字节时发生 IO 异常
	 */
	public DetectResult detect(Path imagePath) {
		if (imagePath == null) {
			throw new IllegalArgumentException("imagePath must not be null");
		}
		try {
			Mat mat = Imgcodecs.imread(imagePath.toFile().getAbsolutePath());
			if (mat.empty()) {
				mat.release();
				throw new IllegalArgumentException("Failed to load image: " + imagePath);
			}
			try {
				return detectMat(mat);
			} finally {
				mat.release();
			}
		} catch (UnsupportedOperationException ignore) {
			try {
				return detect(Files.readAllBytes(imagePath));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * 文本检测（仅检测，不识别）。
	 *
	 * <p>典型场景：Spring Boot 上传 {@code MultipartFile.getBytes()}。
	 *
	 * @param imgBytes 图片字节
	 * @return boxes 形状 (N, 4, 2) int，scores 长度 N
	 */
	public DetectResult detect(byte[] imgBytes) {
		Mat mat = decodeMat(imgBytes);
		try {
			return detectMat(mat);
		} finally {
			mat.release();
		}
	}

	// ==================================================================
	// 内部/高级用法：Mat 入参，调用方负责 release
	// ==================================================================

	/**
	 * 文本检测（Mat 版）。
	 *
	 * <p>仅适用于「已持有 Mat 并需复用」的高级场景（如同一图跑多次推理）；
	 * Mat 的 release 由调用方负责。一般场景请使用 {@link #detect(String)} / {@link #detect(byte[])} 等重载。
	 *
	 * @param imgBgr BGR 格式图像 (H, W, 3) uint8
	 * @return boxes 形状 (N, 4, 2) int，scores 长度 N
	 */
	public DetectResult detectMat(Mat imgBgr) {
		requireOpen();
		DetectionPreprocessor.Result prep = detPre.call(imgBgr);
		long[] shape = toLongArray(prep.shape());
		FloatBuffer buf = NdArrayUtils.toBuffer(prep.data());
		try (
			OnnxTensor input = OnnxTensor.createTensor(env, buf, shape);
			OrtSession.Result result = detSession.run(Map.of(detInputName, input))
		) {
			OnnxTensor outTensor = (OnnxTensor) result.get(0);
			float[][] prob = readProb2D(outTensor);
			Mat probMat = probToMat(prob, prep.imgShape());
			try {
				DbPostProcessor.Result post = detPost.call(probMat, prep.imgShape());
				return new DetectResult(post.boxes(), post.scores());
			} finally {
				probMat.release();
			}
		} catch (OrtException e) {
			throw new RuntimeException("det 推理失败: " + e.getMessage(), e);
		}
	}

	/**
	 * 文本识别（Mat 版，支持批量）。
	 *
	 * <p>仅适用于「已持有 Mat 并需复用」的高级场景（如同一图跑多次推理）；
	 * 每个 crop Mat 的 release 由调用方负责。一般场景请使用 {@link #run(String)} 等重载。
	 *
	 * @param imgList 裁剪后的 BGR 文本行图像列表
	 * @return texts 与 scores 长度一致
	 */
	public RecognizeResult recognizeMat(List<Mat> imgList) {
		requireOpen();
		int n = imgList.size();
		if (n == 0) {
			return new RecognizeResult(new String[0], new float[0]);
		}
		if (log.isDebugEnabled()) {
			Mat first = imgList.get(0);
			log.debug("rec 输入 #0: {}x{}x{} type={} (BGR)", first.rows(), first.cols(), first.channels(), first.type());
		}

		List<Integer> order = new ArrayList<>(n);
		List<Double> ratios = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			Mat m = imgList.get(i);
			order.add(i);
			ratios.add((double) m.cols() / m.rows());
		}
		List<Integer> sortedOrder = new ArrayList<>(order);
		sortedOrder.sort(Comparator.comparingDouble(ratios::get));

		List<Mat> sortedImgs = new ArrayList<>(n);
		for (int idx : sortedOrder) {
			sortedImgs.add(imgList.get(idx));
		}

		String[] texts = new String[n];
		float[] scores = new float[n];

		for (int start = 0; start < n; start += recBatchSize) {
			int end = Math.min(start + recBatchSize, n);
			List<Mat> batch = sortedImgs.subList(start, end);
			RecognitionPreprocessor.Result prep = recPre.call(batch);
			long[] shape = toLongArray(prep.shape());
			FloatBuffer buf = NdArrayUtils.toBuffer(prep.data());
			try (
				OnnxTensor input = OnnxTensor.createTensor(env, buf, shape);
				OrtSession.Result result = recSession.run(Map.of(recInputName, input))
			) {
				OnnxTensor outTensor = (OnnxTensor) result.get(0);
				float[][][] modelOutput = read3D(outTensor);
				CtcLabelDecoder.Result decoded = recPost.call(modelOutput);
				for (int j = 0; j < decoded.texts().length; j++) {
					int orig = sortedOrder.get(start + j);
					texts[orig] = decoded.texts()[j];
					scores[orig] = decoded.scores()[j];
				}
			} catch (OrtException e) {
				throw new RuntimeException("rec 推理失败: " + e.getMessage(), e);
			}
		}
		return new RecognizeResult(texts, scores);
	}

	/**
	 * 完整 OCR 流程（Mat 版）：检测 → 排序 → 裁剪 → 识别。
	 *
	 * <p>仅适用于「已持有 Mat 并需复用」的高级场景（如同一图跑多次推理）；
	 * Mat 的 release 由调用方负责。一般场景请使用 {@link #run(String)} / {@link #run(byte[])} / {@link #run(Path)} 等重载。
	 *
	 * @param imgBgr BGR 格式图像 (H, W, 3) uint8
	 * @return 识别结果列表（按阅读顺序排列）
	 */
	public List<PPOcrV6Result> runMat(Mat imgBgr) {
		requireOpen();
		DetectResult dr = detectMat(imgBgr);
		if (dr.boxes().length == 0) {
			return List.of();
		}

		int[][][] sortedBoxes = BoxUtil.sortQuadBoxes(dr.boxes());

		List<Mat> crops = CropUtil.cropByPolys(imgBgr, sortedBoxes);
		try {
			List<int[][]> validBoxes = new ArrayList<>();
			List<Mat> validCrops = new ArrayList<>();
			for (int i = 0; i < sortedBoxes.length; i++) {
				if (crops.get(i) != null) {
					validBoxes.add(sortedBoxes[i]);
					validCrops.add(crops.get(i));
				}
			}
			if (validCrops.isEmpty()) {
				return List.of();
			}

			RecognizeResult rr = recognizeMat(validCrops);

			List<PPOcrV6Result> results = new ArrayList<>(validBoxes.size());
			for (int i = 0; i < validBoxes.size(); i++) {
				results.add(new PPOcrV6Result(rr.texts()[i], rr.scores()[i], validBoxes.get(i)));
			}
			return results;
		} finally {
			for (Mat crop : crops) {
				if (crop != null) {
					crop.release();
				}
			}
		}
	}

	// ==================================================================
	// 内部工具
	// ==================================================================

	/**
	 * 将图片字节解码为 BGR Mat。
	 *
	 * @param imgBytes 图片字节
	 * @return BGR 格式的 Mat（非空）
	 * @throws IllegalArgumentException 字节为空或解码失败
	 */
	private static Mat decodeMat(byte[] imgBytes) {
		if (imgBytes == null || imgBytes.length == 0) {
			throw new IllegalArgumentException("imgBytes must not be empty");
		}
		Mat mat = Imgcodecs.imdecode(new MatOfByte(imgBytes), Imgcodecs.IMREAD_COLOR);
		if (mat.empty()) {
			mat.release();
			throw new IllegalArgumentException("Failed to decode image from byte[] (unsupported format or corrupted data)");
		}
		return mat;
	}

	private long[] toLongArray(int[] arr) {
		long[] out = new long[arr.length];
		for (int i = 0; i < arr.length; i++) {
			out[i] = arr[i];
		}
		return out;
	}

	private float[][] readProb2D(OnnxTensor tensor) throws OrtException {
		FloatBuffer buf = tensor.getFloatBuffer();
		long[] shape = tensor.getInfo().getShape();
		int total = (int) (shape[0] * shape[1] * shape[2] * shape[3]);
		float[] data = new float[total];
		buf.get(data);
		int h = (int) shape[2];
		int w = (int) shape[3];
		float[][] out = new float[h][w];
		for (int i = 0; i < h; i++) {
			System.arraycopy(data, i * w, out[i], 0, w);
		}
		return out;
	}

	private float[][][] read3D(OnnxTensor tensor) throws OrtException {
		FloatBuffer buf = tensor.getFloatBuffer();
		long[] shape = tensor.getInfo().getShape();
		if (shape.length != 3) {
			throw new IllegalArgumentException("期望 3D rec 输出, 实际 " + shape.length + "D");
		}
		int b = (int) shape[0];
		int t = (int) shape[1];
		int c = (int) shape[2];
		float[] data = new float[b * t * c];
		buf.get(data);
		float[][][] out = new float[b][t][c];
		for (int i = 0; i < b; i++) {
			for (int j = 0; j < t; j++) {
				System.arraycopy(data, (i * t + j) * c, out[i][j], 0, c);
			}
		}
		return out;
	}

	private Mat probToMat(float[][] prob, float[] imgShape) {
		int h = prob.length;
		int w = prob[0].length;
		Mat m = new Mat(h, w, org.opencv.core.CvType.CV_32F);
		try {
			float[] flat = new float[h * w];
			for (int i = 0; i < h; i++) {
				System.arraycopy(prob[i], 0, flat, i * w, w);
			}
			m.put(0, 0, flat);
			return m;
		} catch (RuntimeException | Error e) {
			m.release();
			throw e;
		}
	}

	// ==================================================================
	// 内部记录
	// ==================================================================

	/**
	 * 检测结果。
	 *
	 * @param boxes  文本框 (N, 4, 2) int
	 * @param scores 每框分数
	 */
	public record DetectResult(int[][][] boxes, float[] scores) {
	}

	/**
	 * 识别结果。
	 *
	 * @param texts  识别文本
	 * @param scores 每条文本的置信度
	 */
	public record RecognizeResult(String[] texts, float[] scores) {
	}
}

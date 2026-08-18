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

package net.dreamlu.mica.ai.ppocr.structured.parser.core;

import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化解析调试 demo 泛型基类。
 *
 * <p>封装 OpenCV 加载、模型初始化、OCR 推理、可视化等通用流程，
 * 子类只需指定：
 * <ul>
 *   <li>{@link #newParser(PPOcrV6Engine)} —— 返回绑定好泛型的 {@link BaseStructuredParser} 实例；</li>
 *   <li>{@link #printResult(Object)} —— 按证件类型输出字段；</li>
 * </ul>
 *
 * <p>典型子类（{@code VehicleLicenseMain} 仅 30 行）：
 * <pre>{@code
 * public class VehicleLicenseMain extends BaseTest<VehicleLicenseParser, VehicleLicenseResult> {
 *
 *     private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";
 *     private static final String VIS_PATH   = "test_images/vehicle/vis.png";
 *
 *     public static void main(String[] args) {
 *         new VehicleLicenseMain().demo(IMAGE_PATH, VIS_PATH);
 *     }
 *
 *     @Override protected VehicleLicenseParser newParser(PPOcrV6Engine engine) {
 *         return new VehicleLicenseParser(engine);
 *     }
 *
 *     @Override protected void printResult(VehicleLicenseResult r) {
 *         System.out.println("plateNo:     " + r.getPlateNo());
 *         // ... 其他字段
 *     }
 * }
 * }</pre>
 *
 * @param <P> 解析器类型
 * @param <R> 解析结果类型
 */
public abstract class BaseTest<P extends BaseStructuredParser<R>, R> {

	/**
	 * 模型档位：tiny / small / medium
	 */
	protected static final String TIER = "small";

	/**
	 * 是否启用文档方向分类（PP-OCRv6 use_doc_orientation_classify）
	 */
	protected static final boolean USE_DOC_ORIENTATION = true;

	/**
	 * 新建一个解析器实例（{@code engine} 传 null 即可，本基类已自行管理 OCR）。
	 *
	 * @return 解析器实例
	 */
	protected abstract P newParser(PPOcrV6Engine engine);

	/**
	 * 打印单个结构化结果（按证件类型输出字段）。
	 *
	 * @param result 解析结果
	 */
	protected abstract void printResult(R result);

	/**
	 * demo 入口：跑 OCR + 打印结构化结果 + 可视化。
	 *
	 * @param imagePath 推理图片路径
	 * @param visPath   可视化输出路径（可为 null）
	 */
	public final void demo(String imagePath, String visPath) {
		// 加载 OpenCV 原生库
		nu.pattern.OpenCV.loadLocally();
		// 读取图片
		System.out.println("Image:  " + imagePath);
		Mat img = Imgcodecs.imread(imagePath);
		if (img == null || img.empty()) {
			System.err.println("Error: cannot read image: " + imagePath);
			System.exit(1);
		}
		List<PPOcrV6Result> results = runOcr(img);
		if (visPath != null) {
			saveVis(img, results, visPath);
		}
		img.release();
	}

	/**
	 * 解析 OCR 结果 + 打印结构化字段。
	 *
	 * <p>子类无需重写；如需自定义输出格式可重写 {@link #printResult(Object)}。
	 *
	 * @param results OCR 结果列表
	 */
	protected void printResults(PPOcrV6Engine engine, List<PPOcrV6Result> results) {
		P parser = newParser(engine);
		R result = parser.parseResults(results);
		System.out.println();
		printResult(result);
	}

	/**
	 * 加载 OpenCV 原生库 + 跑 OCR 推理。
	 *
	 * @param image 已读取的图片
	 * @return OCR 结果列表
	 */
	protected List<PPOcrV6Result> runOcr(Mat image) {
		String detModel = "models/ppocr-v6/" + TIER + "/det.onnx";
		String recModel = "models/ppocr-v6/" + TIER + "/rec.onnx";
		String dict = "models/ppocr-v6/" + TIER + "/dict.txt";
		String docOriModel = "models/ppocr-v6/doc_ori/doc_ori.onnx";

		System.out.println("Det:    " + detModel);
		System.out.println("Rec:    " + recModel);
		System.out.println("Dict:   " + dict);
		System.out.println("DocOri: " + docOriModel + " (enabled=" + USE_DOC_ORIENTATION + ")");
		System.out.println("Size:   " + image.cols() + "x" + image.rows());

		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath(detModel)
			.recModelPath(recModel)
			.recCharDictPath(dict)
			.useDocOrientationClassify(USE_DOC_ORIENTATION)
			.docOrientationModelPath(docOriModel)
			.build();

		long t0 = System.currentTimeMillis();
		List<PPOcrV6Result> results;
		try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
			System.out.println("Running OCR...");
			results = engine.runMat(image);

			// 打印 OCR 结果
			long elapsed = System.currentTimeMillis() - t0;
			System.out.println("\nDetected " + results.size() + " text regions (elapsed " + elapsed + " ms):\n");
			for (int i = 0; i < results.size(); i++) {
				PPOcrV6Result r = results.get(i);
				int[][] b = r.box();
				System.out.printf("  [%2d] text=\"%s\"  score=%.6f  box=[(%d,%d),(%d,%d),(%d,%d),(%d,%d)]%n",
					i + 1, r.text(), r.score(),
					b[0][0], b[0][1], b[1][0], b[1][1], b[2][0], b[2][1], b[3][0], b[3][1]);
			}

			// 打印结构化结果
			printResults(engine,  results);
		}

		return results;
	}

	/**
	 * 在原图上绘制检测框并保存为 PNG。
	 *
	 * <p>如果 OCR 启用了 doc_ori（{@link PPOcrV6Result#rotatedDegrees()} 非 0），
	 * 会先通过 {@link PPOcrV6Result#boxInOriginalImg(int, int)} 把文本框投影回
	 * 原图坐标系再绘制，避免「原图 vs 旋转后 box」的错位。
	 *
	 * @param img    原图
	 * @param results OCR 结果列表
	 * @param out    输出 PNG 路径
	 */
	protected void saveVis(Mat img, List<PPOcrV6Result> results, String out) {
		Mat canvas = img.clone();
		int imgW = img.cols();
		int imgH = img.rows();
		for (PPOcrV6Result r : results) {
			int[][] box = r.boxInOriginalImg(imgW, imgH);
			Point[] pts = new Point[4];
			for (int i = 0; i < 4; i++) {
				pts[i] = new Point(box[i][0], box[i][1]);
			}
			MatOfPoint mop = new MatOfPoint(pts);
			List<MatOfPoint> list = new ArrayList<>();
			list.add(mop);
			Imgproc.polylines(canvas, list, true, new Scalar(0, 255, 0), 2);
		}
		boolean ok = Imgcodecs.imwrite(out, canvas);
		if (ok) {
			System.out.println("\nVisualization saved: " + out);
		} else {
			System.err.println("Warning: failed to save visualization: " + out);
		}
		canvas.release();
	}
}

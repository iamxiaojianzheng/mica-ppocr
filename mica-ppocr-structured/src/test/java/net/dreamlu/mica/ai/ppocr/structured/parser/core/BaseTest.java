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
 * 结构化解析调试 demo 基类。
 *
 * <p>封装 OpenCV 加载、模型初始化、OCR 推理、可视化等通用流程，
 * 子类只需关注具体的结构化解析逻辑与字段打印。
 *
 * <p>使用方式：每个证件类型建一个 {@code XxxMain}，继承本类，重写 {@link #printResults(List)}。
 */
public abstract class BaseTest {

	/**
	 * 模型档位：tiny / small / medium
	 */
	protected static final String TIER = "small";

	/**
	 * 是否启用文档方向分类（PP-OCRv6 use_doc_orientation_classify）
	 */
	protected static final boolean USE_DOC_ORIENTATION = true;

	/**
	 * 文档方向分类置信度阈值；低于此值视为 0°（不旋转）。
	 * 默认 0.5 较保守，PP-OCRv6 实际图片 4 类概率可能接近 25% 随机分布，
	 * 推荐降到 0.3 以让 doc_ori 在弱信号下也能起效。
	 */
	protected static final float DOC_ORIENTATION_THRESH = 0.3f;

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
			.docOrientationThresh(DOC_ORIENTATION_THRESH)
			.build();

		long t0 = System.currentTimeMillis();
		List<PPOcrV6Result> results;
		try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
			System.out.println("Running OCR...");
			results = engine.runMat(image);
		}
		// 调试提示：doc_ori 已集成到 runMat 内部分类旋转，看输出是否变正常
		System.out.println("Detected " + results.size() + " text regions.");
		long elapsed = System.currentTimeMillis() - t0;
		System.out.println("\nDetected " + results.size() + " text regions (elapsed " + elapsed + " ms):\n");
		for (int i = 0; i < results.size(); i++) {
			PPOcrV6Result r = results.get(i);
			int[][] b = r.box();
			System.out.printf("  [%2d] text=\"%s\"  score=%.6f  box=[(%d,%d),(%d,%d),(%d,%d),(%d,%d)]%n",
				i + 1, r.text(), r.score(),
				b[0][0], b[0][1], b[1][0], b[1][1], b[2][0], b[2][1], b[3][0], b[3][1]);
		}
		return results;
	}

	/**
	 * 在原图上绘制检测框并保存为 PNG。
	 *
	 * @param img    原图
	 * @param results OCR 结果列表
	 * @param out    输出 PNG 路径
	 */
	protected void saveVis(Mat img, List<PPOcrV6Result> results, String out) {
		Mat canvas = img.clone();
		for (PPOcrV6Result r : results) {
			Point[] pts = new Point[4];
			for (int i = 0; i < 4; i++) {
				pts[i] = new Point(r.box()[i][0], r.box()[i][1]);
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

	/**
	 * 子类实现：打印结构化解析结果。
	 *
	 * @param results OCR 结果列表
	 */
	protected abstract void printResults(List<PPOcrV6Result> results);

	/**
	 * demo 入口：跑 OCR + 打印结构化结果 + 可视化。
	 *
	 * @param imagePath 推理图片路径
	 * @param visPath   可视化输出路径（可为 null）
	 */
	protected void demo(String imagePath, String visPath) {
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
		printResults(results);
		if (visPath != null) {
			saveVis(img, results, visPath);
		}
	}
}

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

package net.dreamlu.mica.ai.ppocr.test;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
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
 * 直接运行 main 即可对一张图片进行 OCR 推理，便于本地快速验证。
 *
 * <p>模型与图片路径写死在源码里，按需修改。
 */
@Slf4j
@UtilityClass
public class Main {

	/**
	 * 模型档位：tiny / small / medium
	 */
	private static final String TIER = "tiny";
	/**
	 * 推理图片路径，相对工程根目录
	 */
	private static final String IMAGE_PATH = "test_images/general_ocr_002.png";
	/**
	 * 可视化结果输出路径，传 null 跳过可视化
	 */
	private static final String VIS_PATH = "test_images/output_vis.png";

	public static void main(String[] args) {
		nu.pattern.OpenCV.loadShared();

		String detModel = "models/ppocr-v6/" + TIER + "/det.onnx";
		String recModel = "models/ppocr-v6/" + TIER + "/rec.onnx";
		String dict = "models/ppocr-v6/" + TIER + "/dict.txt";

		System.out.println("Image:  " + IMAGE_PATH);
		System.out.println("Det:    " + detModel);
		System.out.println("Rec:    " + recModel);
		System.out.println("Dict:   " + dict);

		Mat img = Imgcodecs.imread(IMAGE_PATH);
		if (img == null || img.empty()) {
			System.err.println("Error: cannot read image: " + IMAGE_PATH);
			System.exit(1);
		}
		System.out.println("Size:   " + img.cols() + "x" + img.rows());

		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath(detModel)
			.recModelPath(recModel)
			.recCharDictPath(dict)
			.build();

		long t0 = System.currentTimeMillis();
		List<PPOcrV6Result> results;
		try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
			System.out.println("Running OCR...");
			results = engine.run(img);
		}
		long elapsed = System.currentTimeMillis() - t0;
		System.out.println("\nDetected " + results.size() + " text regions (elapsed " + elapsed + " ms):\n");
		for (int i = 0; i < results.size(); i++) {
			PPOcrV6Result r = results.get(i);
			int[][] b = r.box();
			System.out.printf("  [%2d] text=\"%s\"  score=%.6f  box=[(%d,%d),(%d,%d),(%d,%d),(%d,%d)]%n",
				i + 1, r.text(), r.score(),
				b[0][0], b[0][1], b[1][0], b[1][1], b[2][0], b[2][1], b[3][0], b[3][1]);
		}

		if (VIS_PATH != null) {
			saveVis(img, results, VIS_PATH);
		}
	}

	/**
	 * 在原图上绘制检测框并保存为 PNG。
	 */
	private static void saveVis(Mat img, List<PPOcrV6Result> results, String out) {
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
}

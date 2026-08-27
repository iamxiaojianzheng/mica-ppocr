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

import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 发票结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的发票图片，运行 main 即可输出 OCR 框 + 结构化字段 + 可视化。
 */
public class InvoiceMain {

	private static final String IMAGE_PATH = "test_images/invoice/invoice1.jpg";
	private static final String VIS_PATH = "test_images/invoice/vis.png";

	public static void main(String[] args) throws IOException {
		nu.pattern.OpenCV.loadLocally();
		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath("models/ppocr-v6/tiny/det.onnx")
			.recModelPath("models/ppocr-v6/tiny/rec.onnx")
			.recCharDictPath("models/ppocr-v6/tiny/dict.txt")
			.useDocOrientationClassify(true)
			.docOrientationModelPath("models/ppocr-v6/doc_ori/doc_ori.onnx")
			.build();

		Mat img = Imgcodecs.imread(IMAGE_PATH);
		if (img == null || img.empty()) {
			System.err.println("无法读取图片: " + IMAGE_PATH);
			return;
		}
		try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
			List<PPOcrV6Result> results = engine.runMat(img);
			System.out.println("Detected " + results.size() + " text regions:\n");
			for (PPOcrV6Result r : results) {
				int[][] b = r.box();
				System.out.printf("  text=\"%s\"  score=%.6f  box=[(%d,%d),(%d,%d)]%n",
					r.text(), r.score(), b[0][0], b[0][1], b[2][0], b[2][1]);
			}
			System.out.println("\n--- 结构化解析 ---");
			InvoiceParser dispatcher = new InvoiceParser(engine);
			printResult(dispatcher.parseResults(results));
			saveVis(img, results, VIS_PATH);
		} finally {
			img.release();
		}
	}

	private static void printResult(InvoiceResult inv) {
		System.out.println("发票代码       " + inv.getInvoiceCode());
		System.out.println("发票号码       " + inv.getInvoiceNo());
		System.out.println("开票日期       " + inv.getInvoiceDate());
		System.out.println();
		System.out.println("--- 购买方 ---");
		System.out.println("名称           " + inv.getBuyerName());
		System.out.println("税号           " + inv.getBuyerTaxNo());
		System.out.println("地址电话       " + inv.getBuyerAddressPhone());
		System.out.println("开户行账号     " + inv.getBuyerBankAccount());
		System.out.println();
		System.out.println("--- 销售方 ---");
		System.out.println("名称           " + inv.getSellerName());
		System.out.println("税号           " + inv.getSellerTaxNo());
		System.out.println("地址电话       " + inv.getSellerAddressPhone());
		System.out.println("开户行账号     " + inv.getSellerBankAccount());
		System.out.println();
		System.out.println("--- 明细 ---");
		for (InvoiceItem item : inv.getItems()) {
			System.out.println("商品/服务名称  " + item.getGoodsName());
			System.out.println("金额           " + item.getAmount());
			System.out.println("税率           " + item.getTaxRate());
			System.out.println("税额           " + item.getTaxAmount());
		}
		System.out.println();
		System.out.println("--- 合计 ---");
		System.out.println("价税合计(大写) " + inv.getTotalAmountUpper());
		System.out.println("价税合计(小写) " + inv.getTotalAmountLower());
		System.out.println();
		System.out.println("--- 底栏 ---");
		System.out.println("收款人         " + inv.getPayee());
		System.out.println("复核人         " + inv.getReviewer());
		System.out.println("开票人         " + inv.getIssuer());
	}

	private static void saveVis(Mat img, List<PPOcrV6Result> results, String out) {
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
		System.out.println(ok ? "\nVisualization saved: " + out : "Warning: failed to save visualization: " + out);
		canvas.release();
	}
}

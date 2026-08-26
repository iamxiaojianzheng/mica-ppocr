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

package net.dreamlu.mica.ai.ppocr.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.UtilityClass;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 文本框排序与最小面积外接矩形顶点排序。
 *
 * <p>对应 Python 端的 order_minarea_box_points 与 sort_quad_boxes。
 */
@UtilityClass
public class BoxUtil {

	/**
	 * 计算轮廓的最小面积外接矩形并按 [左上, 右上, 右下, 左下] 排序。
	 *
	 * @param contour OpenCV 轮廓（多边形顶点）
	 * @return MinAreaBox 包含 4 顶点（Point）与短边长度
	 */
	public static MinAreaBox orderMinAreaBoxPoints(MatOfPoint contour) {
		MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
		Mat boxMat = null;
		try {
			boxMat = new Mat();
			RotatedRect rrect = Imgproc.minAreaRect(contour2f);
			Imgproc.boxPoints(rrect, boxMat);

			Point[] pts = new Point[4];
			for (int i = 0; i < 4; i++) {
				float[] buf = new float[2];
				boxMat.get(i, 0, buf);
				pts[i] = new Point(buf[0], buf[1]);
			}

			// 按 x 升序
			Point[] sorted = pts.clone();
			Arrays.sort(sorted, Comparator.comparingDouble(p -> p.x));

			// 左侧两个点：y 大者为 TL
			int tlIdx, blIdx;
			if (sorted[1].y > sorted[0].y) {
				tlIdx = 0;
				blIdx = 1;
			} else {
				tlIdx = 1;
				blIdx = 0;
			}
			// 右侧两个点：y 大者为 BR
			int trIdx, brIdx;
			if (sorted[3].y > sorted[2].y) {
				trIdx = 2;
				brIdx = 3;
			} else {
				trIdx = 3;
				brIdx = 2;
			}

			Point[] ordered = new Point[]{
				sorted[tlIdx], sorted[trIdx], sorted[brIdx], sorted[blIdx]
			};
			float minSide = (float) Math.min(rrect.size.width, rrect.size.height);
			return new MinAreaBox(ordered, minSide);
		} finally {
			if (boxMat != null) {
				boxMat.release();
			}
			contour2f.release();
		}
	}

	/**
	 * 重载：接受 float[][] 形式的 (N, 2) 多边形顶点。
	 *
	 * @param polygon 多边形顶点 (N, 2)
	 * @return MinAreaBox 包含 4 顶点（Point）与短边长度
	 */
	public static MinAreaBox orderMinAreaBoxPoints(float[][] polygon) {
		Point[] pts = new Point[polygon.length];
		for (int i = 0; i < polygon.length; i++) {
			pts[i] = new Point(polygon[i][0], polygon[i][1]);
		}
		MatOfPoint mop = new MatOfPoint();
		try {
			mop.fromArray(pts);
			return orderMinAreaBoxPoints(mop);
		} finally {
			mop.release();
		}
	}

	/**
	 * 按阅读顺序（从上到下、从左到右）排序文本框。
	 *
	 * <p>算法：先按 (y0, x0) 主排序；再对相邻 y0 差值 {@code < 10} 的（视为同一行）
	 * 做插入排序稳定重排。时间复杂度 {@code O(n²)}，对典型数十个框可接受。
	 *
	 * @param boxes (N, 4, 2) 文本框数组
	 * @return 排序后的新数组
	 */
	public static int[][][] sortQuadBoxes(int[][][] boxes) {
		int n = boxes.length;
		if (n <= 1) {
			return boxes.clone();
		}

		// 按 (y0, x0) 主排序
		int[][][] sorted = boxes.clone();
		Arrays.sort(sorted, Comparator.<int[][]>comparingInt(b -> b[0][1])
			.thenComparingInt(b -> b[0][0]));

		// 复制为可交换的列表
		List<int[][]> items = new ArrayList<>(Arrays.asList(sorted));

		for (int i = 0; i < n - 1; i++) {
			for (int j = i; j >= 0; j--) {
				int yNext = items.get(j + 1)[0][1];
				int yCur = items.get(j)[0][1];
				if (Math.abs(yNext - yCur) < 10
					&& items.get(j + 1)[0][0] < items.get(j)[0][0]) {
					// 交换
					int[][] tmp = items.get(j);
					items.set(j, items.get(j + 1));
					items.set(j + 1, tmp);
				} else {
					break;
				}
			}
		}

		int[][][] out = new int[n][][];
		for (int i = 0; i < n; i++) {
			out[i] = items.get(i);
		}
		return out;
	}

	/**
	 * 最小面积外接矩形结果。
	 */
	@Getter
	@ToString
	@EqualsAndHashCode
	@RequiredArgsConstructor
	@Accessors(fluent = true)
	public static class MinAreaBox {
		/**
		 * [左上, 右上, 右下, 左下] 顺序的 4 顶点
		 */
		private final Point[] points;
		/**
		 * 短边长度（像素）
		 */
		private final float minSideLen;

		/**
		 * 将顶点转换为 (4, 2) 的 float 数组。
		 *
		 * @return float[][] 形式的 4 顶点坐标
		 */
		public float[][] asFloatArray() {
			float[][] out = new float[4][2];
			for (int i = 0; i < 4; i++) {
				out[i][0] = (float) points[i].x;
				out[i][1] = (float) points[i].y;
			}
			return out;
		}
	}
}

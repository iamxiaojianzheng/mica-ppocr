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

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本区域裁剪：透视变换 + 长宽比校正。
 *
 * <p>对应 Python 端的 _rotate_crop_image / _minarea_rect_crop / crop_by_polys：
 * <ol>
 *   <li>最小外接矩形：minAreaRect 给出 4 顶点</li>
 *   <li>按四边距离求目标宽高</li>
 *   <li>getPerspectiveTransform + warpPerspective 校正</li>
 *   <li>若高/宽 ≥ 1.5，逆时针旋转 90°（即 np.rot90）</li>
 * </ol>
 */
@Slf4j
public final class CropUtil {

	private CropUtil() {}

	/**
	 * 透视变换裁剪旋转文本区域。
	 *
	 * @param img    源图像 (H, W, 3) uint8
	 * @param points 四个顶点 (4, 2)，顺序为 [左上, 右上, 右下, 左下]
	 * @return 裁剪后的图像；参数非法或透视失败时返回 null
	 */
	public static Mat rotateCropImage(Mat img, float[][] points) {
		if (points.length < 4) {
			return null;
		}
		float[][] pts = new float[4][2];
		System.arraycopy(points, 0, pts, 0, 4);

		double wTop = euclid(pts[0], pts[1]);
		double wBot = euclid(pts[2], pts[3]);
		double hLeft = euclid(pts[0], pts[3]);
		double hRight = euclid(pts[1], pts[2]);
		int cropW = (int) Math.max(wTop, wBot);
		int cropH = (int) Math.max(hLeft, hRight);
		if (cropW < 1 || cropH < 1) {
			return null;
		}

		Mat src = new Mat(4, 1, CvType.CV_32FC2);
		Mat dst = null;
		Mat transform = null;
		try {
			for (int i = 0; i < 4; i++) {
				src.put(i, 0, pts[i][0], pts[i][1]);
			}
			dst = new Mat(4, 1, CvType.CV_32FC2);
			dst.put(0, 0, 0, 0,
				cropW, 0,
				cropW, cropH,
				0, cropH);

			try {
				transform = Imgproc.getPerspectiveTransform(src, dst);
			} catch (Exception e) {
				log.debug("Degenerate quadrilateral in perspective crop, skipping: {}", e.getMessage());
				return null;
			}

			Mat cropped = new Mat();
			try {
				Imgproc.warpPerspective(img, cropped, transform, new Size(cropW, cropH),
					Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE, new org.opencv.core.Scalar(0));
			} catch (RuntimeException | Error e) {
				cropped.release();
				throw e;
			}

			if ((double) cropped.rows() / cropped.cols() >= 1.5) {
				// np.rot90: 逆时针旋转 90°
				Mat rotated = new Mat();
				try {
					Core.rotate(cropped, rotated, Core.ROTATE_90_COUNTERCLOCKWISE);
					return rotated;
				} catch (RuntimeException | Error e) {
					rotated.release();
					throw e;
				} finally {
					cropped.release();
				}
			}
			return cropped;
		} finally {
			if (transform != null) {
				transform.release();
			}
			src.release();
			if (dst != null) {
				dst.release();
			}
		}
	}

	/**
	 * 获取最小面积外接矩形并透视裁剪。
	 *
	 * @param img  源图像 (H, W, 3) uint8
	 * @param poly 四个顶点 (4, 2)
	 * @return 裁剪后的图像；参数非法或透视失败时返回 null
	 */
	public static Mat minAreaRectCrop(Mat img, float[][] poly) {
		if (poly == null || poly.length < 4) {
			return null;
		}
		BoxUtil.MinAreaBox mab = BoxUtil.orderMinAreaBoxPoints(poly);
		return rotateCropImage(img, mab.asFloatArray());
	}

	/**
	 * 根据检测框批量裁剪文本区域。
	 *
	 * @param img     源图像 (H, W, 3) uint8
	 * @param dtPolys (N, 4, 2) 四边形数组
	 * @return 与输入等长的裁剪结果列表；失败条目为 null
	 */
	public static List<Mat> cropByPolys(Mat img, int[][][] dtPolys) {
		List<Mat> results = new ArrayList<>(dtPolys.length);
		try {
			for (int[][] poly : dtPolys) {
				Mat crop = minAreaRectCrop(img, polyToFloat(poly));
				if (crop != null && !crop.empty() && crop.rows() > 0 && crop.cols() > 0) {
					results.add(crop);
				} else {
					if (crop != null) {
						crop.release();
					}
					results.add(null);
				}
			}
			return results;
		} catch (RuntimeException | Error e) {
			for (Mat crop : results) {
				if (crop != null) {
					crop.release();
				}
			}
			throw e;
		}
	}

	private static float[][] polyToFloat(int[][] poly) {
		float[][] out = new float[poly.length][2];
		for (int i = 0; i < poly.length; i++) {
			out[i][0] = poly[i][0];
			out[i][1] = poly[i][1];
		}
		return out;
	}

	private static double euclid(float[] a, float[] b) {
		double dx = a[0] - b[0];
		double dy = a[1] - b[1];
		return Math.sqrt(dx * dx + dy * dy);
	}
}

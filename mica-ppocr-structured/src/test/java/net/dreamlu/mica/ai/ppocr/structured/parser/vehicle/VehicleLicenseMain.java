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

package net.dreamlu.mica.ai.ppocr.structured.parser.vehicle;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

import java.util.List;

/**
 * 行驶证结构化解析调试入口。
 *
 * <p>替换 {@code IMAGE_PATH} 为待调试的行驶证图片，运行 main 即可输出 OCR 框
 * + 结构化字段。
 */
public class VehicleLicenseMain extends BaseTest {

	/**
	 * 推理图片路径，相对工程根目录
	 */
	private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";
	/**
	 * 可视化输出路径；传 null 跳过可视化
	 */
	private static final String VIS_PATH = "test_images/vehicle/vis.png";

	public static void main(String[] args) {
		new VehicleLicenseMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected void printResults(List<PPOcrV6Result> results) {
		VehicleLicenseResult license = VehicleLicenseParser.parse(results);
		System.out.println("\n--- 行驶证结构化解析 ---");
		System.out.println("plateNo:      " + license.getPlateNo());
		System.out.println("owner:        " + license.getOwner());
		System.out.println("vehicleType:  " + license.getVehicleType());
		System.out.println("vin:          " + license.getVin());
		System.out.println("issueDate:    " + license.getIssueDate());
	}
}

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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 行驶证结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的行驶证图片，运行 main 即可输出 OCR 框
 * + 结构化字段。
 */
public class VehicleLicenseMain extends BaseTest<VehicleLicenseParser, VehicleLicenseResult> {

	private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";
	private static final String VIS_PATH = "test_images/vehicle/vis.png";

	public static void main(String[] args) {
		new VehicleLicenseMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected VehicleLicenseParser newParser(PPOcrV6Engine engine) {
		return new VehicleLicenseParser(engine);
	}

	@Override
	protected void printResult(VehicleLicenseResult r) {
		System.out.println("plateNo:      " + r.getPlateNo());
		System.out.println("owner:        " + r.getOwner());
		System.out.println("vehicleType:  " + r.getVehicleType());
		System.out.println("vin:          " + r.getVin());
		System.out.println("issueDate:    " + r.getIssueDate());
	}
}

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

package net.dreamlu.mica.ai.ppocr.structured.parser.driver;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 机动车驾驶证结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的驾驶证图片。
 */
public class DriverLicenseMain extends BaseTest<DriverLicenseParser, DriverLicenseResult> {

	private static final String IMAGE_PATH = "test_images/driver/driver1.jpg";
	private static final String VIS_PATH = "test_images/driver/vis.png";

	public static void main(String[] args) {
		new DriverLicenseMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected DriverLicenseParser newParser(PPOcrV6Engine engine) {
		return new DriverLicenseParser(engine);
	}

	@Override
	protected void printResult(DriverLicenseResult r) {
		System.out.println("licenseNumber:    " + r.getLicenseNumber());
		System.out.println("name:             " + r.getName());
		System.out.println("gender:           " + r.getGender());
		System.out.println("nationality:      " + r.getNationality());
		System.out.println("address:          " + r.getAddress());
		System.out.println("birthDate:        " + r.getBirthDate());
		System.out.println("issueDate:        " + r.getIssueDate());
		System.out.println("vehicleClass:     " + r.getVehicleClass());
		System.out.println("issuingAuthority: " + r.getIssuingAuthority());
		System.out.println("validFrom:        " + r.getValidFrom());
		System.out.println("validTo:          " + r.getValidTo());
	}
}

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

package net.dreamlu.mica.ai.ppocr.structured.parser.business;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 营业执照结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的营业执照图片，运行 main 即可输出 OCR 框
 * + 结构化字段。
 */
public class BusinessLicenseMain extends BaseTest<BusinessLicenseParser, BusinessLicenseResult> {

	private static final String IMAGE_PATH = "test_images/business/business1.png";
	private static final String VIS_PATH = "test_images/business/vis.png";

	public static void main(String[] args) {
		new BusinessLicenseMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected BusinessLicenseParser newParser(PPOcrV6Engine engine) {
		return new BusinessLicenseParser(engine);
	}

	@Override
	protected void printResult(BusinessLicenseResult r) {
		System.out.println("社会信用代码          " + r.getCreditCode());
		System.out.println("单位名称              " + r.getName());
		System.out.println("住址                  " + r.getAddress());
		System.out.println("法定代表人            " + r.getLegalPerson());
		System.out.println("有效日期至            " + r.getOperatingPeriod());
		System.out.println("成立日期              " + r.getEstablishDate());
		System.out.println("类型                  " + r.getType());
		System.out.println("注册资本              " + r.getRegisteredCapital());
		System.out.println("经营范围              " + r.getBusinessScope());
	}
}

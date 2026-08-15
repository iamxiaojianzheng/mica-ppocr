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

package net.dreamlu.mica.ai.ppocr.structured.parser.idcard;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 身份证结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的身份证图片（正面或反面均可，解析器内部自动判定）。
 */
public class IdCardMain extends BaseTest<IdCardParser, IdCardResult> {

	private static final String IMAGE_PATH = "test_images/idcard/idcard1.jpg";
	private static final String VIS_PATH = "test_images/idcard/vis.png";

	public static void main(String[] args) {
		new IdCardMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected IdCardParser newParser(PPOcrV6Engine engine) {
		return new IdCardParser(engine);
	}

	@Override
	protected void printResult(IdCardResult r) {
		System.out.println("side:         " + r.getSide());
		System.out.println("name:         " + r.getName());
		System.out.println("gender:       " + r.getGender());
		System.out.println("nation:       " + r.getNation());
		System.out.println("birthDate:    " + r.getBirthDate());
		System.out.println("address:      " + r.getAddress());
		System.out.println("idNumber:     " + r.getIdNumber());
		System.out.println("issuingAuthority: " + r.getIssuingAuthority());
		System.out.println("validFrom:    " + r.getValidFrom());
		System.out.println("validTo:      " + r.getValidTo());
		if (r.getSide() == IdCardSide.UNKNOWN) {
			System.out.println("(warning: 未识别到正反面特征标签)");
		}
	}
}

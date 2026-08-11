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

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

import java.util.List;

/**
 * 身份证结构化解析调试入口。
 *
 * <p>替换 {@code IMAGE_PATH} 为待调试的身份证图片（正面或反面均可，解析器内部自动判定）。
 */
public class IdCardMain extends BaseTest {

	/**
	 * 推理图片路径，相对工程根目录
	 */
	private static final String IMAGE_PATH = "test_images/idcard/idcard1.jpg";
	/**
	 * 可视化输出路径；传 null 跳过可视化
	 */
	private static final String VIS_PATH = "test_images/idcard/vis.png";

	public static void main(String[] args) {
		new IdCardMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected void printResults(List<PPOcrV6Result> results) {
		IdCardResult result = IdCardParser.parse(results);
		System.out.println("\n--- 身份证结构化解析 ---");
		System.out.println("side:         " + result.getSide());
		System.out.println("name:         " + result.getName());
		System.out.println("gender:       " + result.getGender());
		System.out.println("nation:       " + result.getNation());
		System.out.println("birthDate:    " + result.getBirthDate());
		System.out.println("address:      " + result.getAddress());
		System.out.println("idNumber:     " + result.getIdNumber());
		System.out.println("issuingAuthority: " + result.getIssuingAuthority());
		System.out.println("validFrom:    " + result.getValidFrom());
		System.out.println("validTo:      " + result.getValidTo());
	}
}

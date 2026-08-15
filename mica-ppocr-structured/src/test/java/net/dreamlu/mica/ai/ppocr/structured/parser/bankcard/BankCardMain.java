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

package net.dreamlu.mica.ai.ppocr.structured.parser.bankcard;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 银行卡结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的银行卡图片。
 * 当前解析器尚未实现，仅打印 OCR 原始结果用于调试版面定位。
 */
public class BankCardMain extends BaseTest<BankCardParser, BankCardResult> {

	private static final String IMAGE_PATH = "test_images/bankcard/bankcard1.png";
	private static final String VIS_PATH = "test_images/bankcard/vis.png";

	public static void main(String[] args) {
		new BankCardMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected BankCardParser newParser(PPOcrV6Engine engine) {
		return new BankCardParser(engine);
	}

	@Override
	protected void printResult(BankCardResult r) {
		// 注意银行卡有效期格式为 MM/YY 容易被误识别为 MM7YY，可以提升模型为 small 或 medium
		System.out.println("cardNumber:   " + r.getCardNumber());
		System.out.println("validDate:    " + r.getValidDate());
		System.out.println("holderName:   " + r.getHolderName());
		System.out.println("bankName:     " + r.getBankName());
		System.out.println("cardType:     " + r.getCardType());
	}
}

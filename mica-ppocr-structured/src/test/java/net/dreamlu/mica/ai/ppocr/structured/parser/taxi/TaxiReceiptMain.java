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

package net.dreamlu.mica.ai.ppocr.structured.parser.taxi;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 出租车票结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的出租车票图片；
 * 可视化结果将保存到 {@link #VIS_PATH}。
 *
 * <p>默认图片路径 {@code test_images/taxi/taxi1.png}，
 * 缺失时会给出友好提示，不会抛异常。
 */
public class TaxiReceiptMain extends BaseTest<TaxiReceiptParser, TaxiReceiptResult> {

	private static final String IMAGE_PATH = "test_images/taxi/taxi1.png";
	private static final String VIS_PATH = "test_images/taxi/vis.png";

	public static void main(String[] args) {
		new TaxiReceiptMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected TaxiReceiptParser newParser(PPOcrV6Engine engine) {
		return new TaxiReceiptParser(engine);
	}

	@Override
	protected void printResult(TaxiReceiptResult r) {
		System.out.println("--- 票号 ---");
		System.out.println("invoiceCode:       " + r.getInvoiceCode());
		System.out.println("invoiceNo:          " + r.getInvoiceNo());

		System.out.println("\n--- 行程 ---");
		System.out.println("plateNumber:       " + r.getPlateNumber());
		System.out.println("date:              " + r.getDate());
		System.out.println("boardingTime:      " + r.getBoardingTime());
		System.out.println("alightingTime:     " + r.getAlightingTime());
		System.out.println("mileage:           " + r.getMileage());

		System.out.println("\n--- 金额 ---");
		System.out.println("amount:            " + r.getAmount());
		System.out.println("fuelSurcharge:     " + r.getFuelSurcharge());
		System.out.println("bookingFee:        " + r.getBookingFee());
		System.out.println("totalAmount:       " + r.getTotalAmount());

		System.out.println("\n--- 其他 ---");
		System.out.println("city:              " + r.getCity());
	}
}
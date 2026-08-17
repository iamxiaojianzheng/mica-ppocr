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

package net.dreamlu.mica.ai.ppocr.structured.parser.train;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.structured.parser.core.BaseTest;

/**
 * 火车票结构化解析调试入口。
 *
 * <p>替换 {@link #IMAGE_PATH} 为待调试的火车票图片；
 * 可视化结果将保存到 {@link #VIS_PATH}。
 *
 * <p>默认图片路径 {@code test_images/train/train1.png}，
 * 缺失时会给出友好提示，不会抛异常。
 */
public class TrainTicketMain extends BaseTest<TrainTicketParser, TrainTicketResult> {

	private static final String IMAGE_PATH = "test_images/train/train1.png";
	private static final String VIS_PATH = "test_images/train/vis.png";

	public static void main(String[] args) {
		new TrainTicketMain().demo(IMAGE_PATH, VIS_PATH);
	}

	@Override
	protected TrainTicketParser newParser(PPOcrV6Engine engine) {
		return new TrainTicketParser(engine);
	}

	@Override
	protected void printResult(TrainTicketResult r) {
		System.out.println("--- 行程 ---");
		System.out.println("departure:        " + r.getDeparture());
		System.out.println("arrival:          " + r.getArrival());
		System.out.println("trainNumber:      " + r.getTrainNumber());
		System.out.println("departureDate:    " + r.getDepartureDate());
		System.out.println("departureTime:    " + r.getDepartureTime());
		System.out.println("seatNumber:       " + r.getSeatNumber());
		System.out.println("seatClass:        " + r.getSeatClass());

		System.out.println("\n--- 乘客 ---");
		System.out.println("passengerName:    " + r.getPassengerName());
		System.out.println("idNumber:         " + r.getIdNumber());

		System.out.println("\n--- 金额 ---");
		System.out.println("amount:           " + r.getAmount());
		System.out.println("amountExcludingTax: " + r.getAmountExcludingTax());

		System.out.println("\n--- 票号 ---");
		System.out.println("ticketNo:         " + r.getTicketNo());
		System.out.println("invoiceNo:        " + r.getInvoiceNo());
		System.out.println("eTicketNo:        " + r.getETicketNo());

		System.out.println("\n--- 其他 ---");
		System.out.println("invoiceDate:      " + r.getInvoiceDate());
		System.out.println("sellStation:      " + r.getSellStation());
		System.out.println("serialNumber:     " + r.getSerialNumber());
		System.out.println("changedFlag:      " + r.getChangedFlag());
	}
}
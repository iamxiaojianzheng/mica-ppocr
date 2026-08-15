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

package net.dreamlu.mica.ai.ppocr.structured.parser.core;

import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;

import java.util.List;

/**
 * 结构化解析器单元测试基类。
 *
 * <p>集中提供：
 * <ul>
 *   <li>{@link #box(String, int, int, int, int)} —— 构造 mock OCR 文本框；</li>
 *   <li>{@link #parse(BaseStructuredParser, List)} —— 调用解析器实例的 {@code parseResults}。</li>
 * </ul>
 *
 * <p>子类继承后即可获得这两个工具方法，无需重复样板代码。
 */
public abstract class ParserTestSupport {

	/**
	 * 构造 OCR 文本框（用于单元测试 mock）。
	 *
	 * @param text 识别文本
	 * @param x0   左上角 x
	 * @param y0   左上角 y
	 * @param x1   右下角 x
	 * @param y1   右下角 y
	 * @return mock 的 OCR 结果
	 */
	protected static PPOcrV6Result box(String text, int x0, int y0, int x1, int y1) {
		return new PPOcrV6Result(text, 1.0f, new int[][]{
			{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}
		});
	}

	/**
	 * 调用解析器（仅供单元测试 mock results 使用）。
	 *
	 * @param parser  解析器实例（建议 {@code new XxxParser(null)}）
	 * @param results OCR 结果列表
	 * @param <R>     业务结果类型
	 * @return 解析结果
	 */
	protected static <R> R parse(BaseStructuredParser<R> parser, List<PPOcrV6Result> results) {
		return parser.parseResults(results);
	}
}

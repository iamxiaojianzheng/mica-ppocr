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
 * 结构化解析器 SPI：把 OCR 识别出的散落文字框，组织成业务字段对象。
 *
 * <p>实现类只需专注于"字段定义 + 校验"，定位/匹配/兜底等通用能力已下沉到
 * {@link LabelMatcher}。
 *
 * <p>典型实现（实例方法形式，便于通过 DI 注入）：
 * <pre>{@code
 * public final class IdCardParser implements BaseStructuredParser<IdCardResult> {
 *     @Override
 *     public IdCardResult parseResults(List<PPOcrV6Result> results) {
 *         IdCardResult r = new IdCardResult();
 *         r.setName(LabelMatcher.matchValue(results, "姓名"));
 *         r.setIdNo(LabelMatcher.labelOrFallback(
 *             LabelMatcher.matchValue(results, "公民身份号码"),
 *             results, ID_NO_PATTERN, "身份证号", false));
 *         return r;
 *     }
 * }
 * }</pre>
 *
 * <p>对于不需要 DI 的工具类风格，可以直接定义一个静态 {@code parse(List)} 方法，
 * 但此时该类不能再 {@code implements BaseStructuredParser}（Java 不允许静态方法
 * override 接口方法）。建议两者都提供：用 {@code implements} 走 DI，用静态
 * {@code parse} 走工具类调用，内部委托到同一私有方法。
 *
 * @param <R> 业务结果类型
 */
@FunctionalInterface
public interface BaseStructuredParser<R> {

	/**
	 * 从 OCR 结果中解析出业务字段对象。
	 *
	 * @param results OCR 结果列表
	 * @return 结构化结果；解析失败或输入为空时返回的字段值允许为 null
	 */
	R parseResults(List<PPOcrV6Result> results);
}

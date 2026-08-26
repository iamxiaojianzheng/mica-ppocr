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

package net.dreamlu.mica.ai.ppocr.utils;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * ONNX Runtime execution provider 自动选择。
 *
 * <ul>
 *   <li>preferCpu=true → 强制使用 CPUExecutionProvider（保证跨平台 bit-exact 精度）</li>
 *   <li>preferCpu=false → 按 CoreML (macOS) > CUDA > CPU 自动选择</li>
 * </ul>
 */
@Slf4j
@UtilityClass
public class OrtProviders {

	/**
	 * 根据策略解析要启用的 ONNX Runtime provider 列表。
	 *
	 * @param preferCpu true 强制 CPU；false 自动选择加速器
	 * @return ONNX Runtime provider 名称列表
	 */
	public static String[] resolve(boolean preferCpu) {
		if (preferCpu) {
			log.info("ONNX Runtime provider: CPUExecutionProvider (forced)");
			return new String[]{"CPUExecutionProvider"};
		}
		EnumSet<OrtProvider> available;
		try {
			available = OrtEnvironment.getAvailableProviders();
		} catch (Exception e) {
			log.warn("无法枚举 ONNX Runtime providers, 回退到 CPU: {}", e.getMessage());
			return new String[]{"CPUExecutionProvider"};
		}
		List<String> availableNames = new ArrayList<>(available.size());
		for (OrtProvider p : available) {
			availableNames.add(p.getName());
		}
		for (String preferred : CollUtil.listOf("CoreMLExecutionProvider", "CUDAExecutionProvider")) {
			if (availableNames.contains(preferred)) {
				log.info("ONNX Runtime provider: {}", preferred);
				return new String[]{preferred};
			}
		}
		log.info("ONNX Runtime provider: CPUExecutionProvider (fallback)");
		return new String[]{"CPUExecutionProvider"};
	}
}

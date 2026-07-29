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

package net.dreamlu.mica.ai.ppocr.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * OpenCV 原生库初始化。
 *
 * <p>PPOcrV6Engine 内部重度使用 org.opencv.*（Imgproc / Core / Mat）做
 * 图像预处理、文本框后处理和多边形偏移，而 openpnp/opencv 不会在 JVM 启动时自动加载 native 库，
 * 必须在 Spring 容器刷新早期显式调用 OpenCV.loadShared()，否则首次调用
 * Imgproc.xxx 时会抛 UnsatisfiedLinkError。
 *
 * <p>本类以独立的 {@code @AutoConfiguration} 形式注册，并通过
 * {@code @AutoConfigureBefore(PPOCRAutoConfiguration.class)}
 * 保证在 PPOCRAutoConfiguration 创建 PPOcrV6Engine 之前完成 native 加载。
 *
 * <p>启用条件：classpath 存在 nu.pattern.OpenCV（由 openpnp/opencv 传递引入）。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(OpenCV.class)
@AutoConfigureBefore(PPOCRAutoConfiguration.class)
public class OpenCVNativeLoader {

	/**
	 * 注册一个早期初始化的 Bean，用于在 PPOCRAutoConfiguration 之前完成 OpenCV native 加载。
	 *
	 * @return OpenCV native bootstrap
	 */
	@Bean
	public OpenCVNativeBootstrap openCVNativeBootstrap() {
		return new OpenCVNativeBootstrap();
	}

	/**
	 * 通过工厂方法在 Bean 实例化时触发 native 加载，
	 * 确保比 PPOcrV6Engine 更早出现在容器中。
	 */
	public static class OpenCVNativeBootstrap {

		/**
		 * 构造时触发 OpenCV 原生库加载。
		 */
		public OpenCVNativeBootstrap() {
			try {
				OpenCV.loadShared();
				log.info("[mica-ppocr] OpenCV 原生库加载完成: {}", Core.VERSION);
			} catch (Throwable t) {
				log.error("[mica-ppocr] OpenCV 原生库加载失败，PP-OCR Engine 将不可用", t);
			}
		}
	}
}

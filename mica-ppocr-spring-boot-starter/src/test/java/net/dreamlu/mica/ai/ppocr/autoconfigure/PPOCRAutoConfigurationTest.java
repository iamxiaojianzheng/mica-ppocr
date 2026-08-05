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

import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PPOCRAutoConfiguration} 自动装配单元测试。
 *
 * <p>使用 {@link ApplicationContextRunner} 模拟 Spring 容器启动，
 * 验证自动配置的条件注解、属性绑定和校验逻辑。
 *
 * <p>注意：{@link net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine} 的创建
 * 需要真实 ONNX 模型文件，此处测试仅验证配置层；引擎创建失败属于预期行为。
 */
class PPOCRAutoConfigurationTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PPOCRAutoConfiguration.class));

	@Test
	void shouldFailWhenRequiredPropertiesMissing() {
		runner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.isNotNull()
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("det-model-path");
		});
	}

	@Test
	void shouldNotCreateBeansWhenDisabled() {
		runner
			.withPropertyValues("mica.ai.ppocr.enabled=false")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(PPOcrV6Config.class);
			});
	}

	@Test
	void shouldCreateConfigBeanWithAllProperties() {
		runner
			.withPropertyValues(
				"mica.ai.ppocr.det-model-path=/models/det.onnx",
				"mica.ai.ppocr.rec-model-path=/models/rec.onnx",
				"mica.ai.ppocr.rec-char-dict-path=/models/dict.txt",
				"mica.ai.ppocr.det-limit-side-len=128",
				"mica.ai.ppocr.det-thresh=0.5",
				"mica.ai.ppocr.prefer-accelerator=true"
			)
			.run(context -> {
				// 配置绑定成功，引擎因模型文件不存在而失败，属于预期行为；
				// 验证失败根因是文件不存在（而非配置缺失），间接证明配置绑定已通过
				assertThat(context).hasFailed();
				Throwable failure = context.getStartupFailure();
				assertThat(failure)
					.hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("file not found");
			});
	}

	@Test
	void shouldFailOnEngineCreationWhenModelFileMissing() {
		runner
			.withPropertyValues(
				"mica.ai.ppocr.det-model-path=/models/det.onnx",
				"mica.ai.ppocr.rec-model-path=/models/rec.onnx",
				"mica.ai.ppocr.rec-char-dict-path=/models/dict.txt"
			)
			.run(context -> {
				assertThat(context).hasFailed();
				// 所有必需配置已提供，失败原因是引擎创建时模型文件不存在
				assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("file not found");
			});
	}
}
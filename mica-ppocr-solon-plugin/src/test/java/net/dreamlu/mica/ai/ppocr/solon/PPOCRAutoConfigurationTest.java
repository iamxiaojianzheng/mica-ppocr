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

package net.dreamlu.mica.ai.ppocr.solon;

import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import org.junit.jupiter.api.Test;
import org.noear.solon.SimpleSolonApp;
import org.noear.solon.Utils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link PPOCRAutoConfiguration} 自动装配单元测试。
 *
 *
 * <p>注意：{@link net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine} 的创建
 * 需要真实 ONNX 模型文件，此处测试仅验证配置层；引擎创建失败属于预期行为。
 */
class PPOCRAutoConfigurationTest {
	@Test
	void shouldNotCreateBeansWhenDisabled() throws Throwable {
		SimpleSolonApp app = new SimpleSolonApp(PPOCRAutoConfigurationTest.class);
		app.cfg().put("mica.ai.ppocr.enabled", "false");
		app.start(null);

		assert null == app.context().getBean(PPOcrV6Config.class);
	}

	@Test
	void shouldCreateConfigBeanWithAllProperties() throws Throwable {
		SimpleSolonApp app = new SimpleSolonApp(PPOCRAutoConfigurationTest.class);
		app.cfg().put("mica.ai.ppocr.enabled", "true");
		app.cfg().put("mica.ai.ppocr.det-model-path", "/models/det.onnx");
		app.cfg().put("mica.ai.ppocr.rec-model-path", "/models/rec.onnx");
		app.cfg().put("mica.ai.ppocr.rec-char-dict-path", "/models/dict.txt");
		app.cfg().put("mica.ai.ppocr.det-limit-side-len", "128");
		app.cfg().put("mica.ai.ppocr.det-thresh", "0.5");
		app.cfg().put("mica.ai.ppocr.prefer-accelerator", "true");

		AtomicReference<Throwable> reference = new AtomicReference<>();
		try {
			app.start(null);
		} catch (Throwable e) {
			e = Utils.throwableUnwrap(e.getCause());
			while (e.getCause() != null){
				e = e.getCause();
			}
			reference.set(e);
		}

		assert reference.get() instanceof IllegalArgumentException;
		assert reference.get().getMessage().contains("file not found");
	}

	@Test
	void shouldFailOnEngineCreationWhenModelFileMissing() throws Throwable {
		SimpleSolonApp app = new SimpleSolonApp(PPOCRAutoConfigurationTest.class);
		app.cfg().put("mica.ai.ppocr.det-model-path", "/models/det.onnx");
		app.cfg().put("mica.ai.ppocr.rec-model-path", "/models/rec.onnx");
		app.cfg().put("mica.ai.ppocr.rec-char-dict-path", "/models/dict.txt");

		AtomicReference<Throwable> reference = new AtomicReference<>();
		try {
			app.start(null);
		} catch (Throwable e) {
			e = Utils.throwableUnwrap(e.getCause());
			while (e.getCause() != null){
				e = e.getCause();
			}
			reference.set(e);
		}

		assert reference.get() instanceof IllegalArgumentException;
		assert reference.get().getMessage().contains("file not found");
	}

	/**
	 * 验证 Solon 配置写 {@code classpath:} 前缀时，{@link PPOcrV6Config} 能正确透传该字符串到 engine。
	 *
	 * <p>本测试先用 yml 设置占位文件路径（绕开 {@code requireNonBlank} 校验），
	 * 再用 {@link PPOCRPropertiesCustomizer} 替换为 {@code classpath:} 路径，
	 * 断言 engine 在 classpath 资源不存在时报告 "classpath resource not found"，
	 * 而非文件系统的 "file not found"，证明 classpath: 前缀被透传给了 engine。
	 */
	@Test
	void shouldAcceptClasspathPrefixAndPropagateToConfig() throws Throwable {
		SimpleSolonApp app = new SimpleSolonApp(PPOCRAutoConfigurationTest.class);
		app.cfg().put("mica.ai.ppocr.det-model-path", "/placeholder/det.onnx");
		app.cfg().put("mica.ai.ppocr.rec-model-path", "/placeholder/rec.onnx");
		app.cfg().put("mica.ai.ppocr.rec-char-dict-path", "/placeholder/dict.txt");
		app.context().wrapAndPut(PPOCRPropertiesCustomizer.class, (PPOCRPropertiesCustomizer) builder -> builder
			.detModelPath("classpath:models/det.onnx")
			.recModelPath("classpath:models/rec.onnx")
			.recCharDictPath("classpath:models/dict.txt"));

		AtomicReference<Throwable> reference = new AtomicReference<>();
		try {
			app.start(null);
		} catch (Throwable e) {
			Throwable root = Utils.throwableUnwrap(e.getCause());
			while (root.getCause() != null) {
				root = root.getCause();
			}
			reference.set(root);
		}

		assert reference.get() instanceof IllegalArgumentException;
		assert reference.get().getMessage().contains("classpath resource not found");
	}
}

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

package net.dreamlu.mica.ai.ppocr.engine;

import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a long-lived engine remains usable after per-run native resources are released,
 * and that constructor-failure paths do not leak file descriptors.
 */
class PPOcrV6EngineResourceTest {
	private static final Path PROC_FD_DIR = Path.of("/proc/self/fd");
	private static final long MAX_FD_DELTA = 2L; // allow tiny /proc fd stream jitter during counting

	@BeforeAll
	static void loadOpenCv() {
		nu.pattern.OpenCV.loadLocally();
	}

	@Test
	void repeatedRunProducesStableResults() {
		Path root = findRepositoryRoot();
		Path modelDir = root.resolve("models/ppocr-v6/tiny");
		Mat image = Imgcodecs.imread(root.resolve("test_images/vehicle/vehicle1.png").toString());
		try {
			assertFalse(image.empty(), "test image should load");

			PPOcrV6Config config = PPOcrV6Config.builder()
				.detModelPath(modelDir.resolve("det.onnx").toString())
				.recModelPath(modelDir.resolve("rec.onnx").toString())
				.recCharDictPath(modelDir.resolve("dict.txt").toString())
				.build();

			try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
				List<String> expected = texts(engine.run(image));
				assertFalse(expected.isEmpty(), "OCR should return at least one result");
				for (int i = 0; i < 2; i++) {
					assertEquals(expected, texts(engine.run(image)));
				}
			}
		} finally {
			image.release();
		}
	}

	@Test
	void shouldCloseOrtSessionsWhenConstructorFailsAfterSessionCreation() throws IOException {
		Assumptions.assumeTrue(Files.isDirectory(PROC_FD_DIR), "requires /proc/self/fd");
		Path root = findRepositoryRoot();
		Path detModel = root.resolve("models/ppocr-v6/tiny/det.onnx");
		Path recModel = root.resolve("models/ppocr-v6/tiny/rec.onnx");
		Path dict = root.resolve("models/ppocr-v6/tiny/dict.txt");
		Assumptions.assumeTrue(Files.isRegularFile(detModel), "requires tiny det model");
		Assumptions.assumeTrue(Files.isRegularFile(recModel), "requires tiny rec model");
		Assumptions.assumeTrue(Files.isRegularFile(dict), "requires tiny dict");

		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath(detModel.toString())
			.recModelPath(recModel.toString())
			.recCharDictPath(dict.toString())
			.detLimitType("invalid")
			.build();

		long before = openFdCount();
		for (int i = 0; i < 20; i++) {
			assertThrows(IllegalArgumentException.class, () -> new PPOcrV6Engine(config));
		}
		long after = openFdCount();
		assertTrue(after - before <= MAX_FD_DELTA, "constructor failure leaked file descriptors: before=" + before + ", after=" + after);
	}

	private static List<String> texts(List<PPOcrV6Result> results) {
		return results.stream().map(PPOcrV6Result::text).toList();
	}

	private static long openFdCount() throws IOException {
		try (Stream<Path> files = Files.list(PROC_FD_DIR)) {
			return files.count();
		}
	}

	private static Path findRepositoryRoot() {
		String multiModuleDir = System.getProperty("maven.multiModuleProjectDirectory");
		if (multiModuleDir != null) {
			return Path.of(multiModuleDir);
		}
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("models/ppocr-v6/tiny"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("repository root with test models not found");
		}
		return current;
	}
}

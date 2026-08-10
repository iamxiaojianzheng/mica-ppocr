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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PPOcrV6EngineResourceTest {
	private static final Path PROC_FD_DIR = Path.of("/proc/self/fd");

	@Test
	void shouldCloseOrtSessionsWhenConstructorFailsAfterSessionCreation() throws IOException {
		Assumptions.assumeTrue(Files.isDirectory(PROC_FD_DIR), "requires /proc/self/fd");
		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath("models/ppocr-v6/tiny/det.onnx")
			.recModelPath("models/ppocr-v6/tiny/rec.onnx")
			.recCharDictPath("models/ppocr-v6/tiny/dict.txt")
			.detLimitType("invalid")
			.build();

		assertThrows(IllegalArgumentException.class, () -> new PPOcrV6Engine(config));

		long before = openFdCount();
		for (int i = 0; i < 20; i++) {
			assertThrows(IllegalArgumentException.class, () -> new PPOcrV6Engine(config));
		}
		long after = openFdCount();
		assertTrue(after - before <= 2, "constructor failure leaked file descriptors: before=" + before + ", after=" + after);
	}

	private static long openFdCount() throws IOException {
		try (Stream<Path> files = Files.list(PROC_FD_DIR)) {
			return files.count();
		}
	}
}

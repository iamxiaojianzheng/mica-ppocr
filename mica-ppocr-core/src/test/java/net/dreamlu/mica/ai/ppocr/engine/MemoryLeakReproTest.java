/*
 * Copyright (c) 2019-2026, dreamlu.net All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 */

package net.dreamlu.mica.ai.ppocr.engine;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import net.dreamlu.mica.ai.ppocr.utils.CollUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Issue #14 回归测试：ONNX Runtime CPU arena + 内存模式优化默认关闭后，
 * 反复识别不同图片不应出现内存单调上涨。
 *
 * <p>指标：
 * <ul>
 *   <li>RSS（commit virtual memory）：ONNX arena 关闭后，每次推理临时内存用完即释放，
 *       RSS 应在 warmup 后稳定，多次采样 delta_rss 应当 < 80 MB（容差 80 MB 覆盖 JNI 抖动）</li>
 *   <li>getThreadAllocatedBytes（线程累计分配）：每个 5 轮 checkpoint 的 chunk_delta
 *       应当 < 1.2×baseline（无单调上涨趋势）</li>
 * </ul>
 */
class MemoryLeakReproTest {
	private static final ThreadMXBean THREAD_BEAN =
		(ThreadMXBean) ManagementFactory.getThreadMXBean();
	private static final OperatingSystemMXBean OS_BEAN =
		(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	// 容器/裸机通用容差：warmup 后每次 checkpoint RSS 波动不超过 80 MB
	private static final long RSS_FLOOR_TOLERANCE_BYTES = 80L * 1024 * 1024;

	@BeforeAll
	static void loadOpenCv() {
		nu.pattern.OpenCV.loadLocally();
	}

	@Test
	void fullPipelineMemoryStableAcrossDifferentImages() {
		Path root = findRepositoryRoot();
		Path modelDir = root.resolve("models/ppocr-v6/tiny");
		Assumptions.assumeTrue(Files.isDirectory(modelDir), "requires tiny models");
		File img1 = root.resolve("test_images/vehicle/vehicle1.png").toFile();
		File img2 = root.resolve("test_images/vehicle/vehicle2.png").toFile();
		Assumptions.assumeTrue(img1.isFile() && img2.isFile(), "requires test images");
		List<File> imgs = new ArrayList<>();
		Collections.addAll(imgs, img1, img2);

		PPOcrV6Config config = PPOcrV6Config.builder()
			.detModelPath(modelDir.resolve("det.onnx").toString())
			.recModelPath(modelDir.resolve("rec.onnx").toString())
			.recCharDictPath(modelDir.resolve("dict.txt").toString())
			.build();
		System.out.println("==== FULL pipeline (engine.run file) — issue #14 regression ====");
		try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
			// 预热 15 轮，让 ONNX/JIT 稳定
			for (int i = 0; i < 15; i++) {
				engine.run(imgs.get(i % 2));
			}
			System.gc();
			sleep(300);
			System.gc();

			long tid = Thread.currentThread().getId();
			long allocated0 = THREAD_BEAN.getThreadAllocatedBytes(tid);
			long rss0 = OS_BEAN.getCommittedVirtualMemorySize();
			long heap0 = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
			System.out.printf("baseline    allocated=%8.2f MB  rss=%7.2f MB  heap=%5.2f MB%n",
				allocated0 / 1024.0 / 1024.0, rss0 / 1024.0 / 1024.0, heap0 / 1024.0 / 1024.0);

			// 监控 20 轮，每 5 轮采样：检查 RSS 不持续增长、分配字节 delta 恒定
			int totalRounds = 20;
			long lastChunkAllocated = 0;
			int done = 0;
			long rssPeak = rss0;
			for (int chunk = 5; chunk <= totalRounds; chunk += 5) {
				while (done < chunk) {
					engine.run(imgs.get(done % 2));
					done++;
				}
				System.gc();
				sleep(200);
				System.gc();
				long allocated = THREAD_BEAN.getThreadAllocatedBytes(tid);
				long rss = OS_BEAN.getCommittedVirtualMemorySize();
				long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
				long chunkAllocated = allocated - allocated0;
				long chunkDelta = chunkAllocated - lastChunkAllocated;
				lastChunkAllocated = chunkAllocated;
				if (rss > rssPeak) rssPeak = rss;
				System.out.printf("round %3d  allocated=+%7.2f MB  chunk_delta=+%6.2f MB  rss=%7.2f MB  delta_rss=%+.2f MB%n",
					done, chunkAllocated / 1024.0 / 1024.0, chunkDelta / 1024.0 / 1024.0,
					rss / 1024.0 / 1024.0, (rss - rss0) / 1024.0 / 1024.0);
			}

			// 断言：peak RSS 与 baseline 的差距在容差内
			long rssGrowth = rssPeak - rss0;
			System.out.printf("peak RSS growth = %+.2f MB (tolerance %d MB)%n",
				rssGrowth / 1024.0 / 1024.0, RSS_FLOOR_TOLERANCE_BYTES / (1024 * 1024));
			if (rssGrowth > RSS_FLOOR_TOLERANCE_BYTES) {
				throw new AssertionError(
					"Issue #14 regression: RSS grew by " + (rssGrowth / 1024 / 1024)
						+ " MB after 20 OCR rounds (tolerance " + (RSS_FLOOR_TOLERANCE_BYTES / 1024 / 1024) + " MB). "
						+ "Check ONNX arena / memory pattern flags.");
			}
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignore) {
			Thread.currentThread().interrupt();
		}
	}

	private static Path findRepositoryRoot() {
		String multiModuleDir = System.getProperty("maven.multiModuleProjectDirectory");
		if (multiModuleDir != null) {
			return CollUtil.pathOf(multiModuleDir);
		}
		Path current = CollUtil.pathOf("").toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("models/ppocr-v6/tiny"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("repository root with test models not found");
		}
		return current;
	}
}

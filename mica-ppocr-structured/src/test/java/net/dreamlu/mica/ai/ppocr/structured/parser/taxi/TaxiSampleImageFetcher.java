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

package net.dreamlu.mica.ai.ppocr.structured.parser.taxi;

import net.dreamlu.mica.ai.ppocr.structured.parser.train.BaiduSampleImageFetcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 出租车票样本图抓取 demo（命令行调试用）。
 *
 * <p>直接复用 {@link BaiduSampleImageFetcher} 抓取出租车票演示页样本。
 *
 * <p>运行：
 * <pre>{@code
 * cd mica-ppocr-structured
 * mvn test-compile
 * java -cp target/test-classes:target/classes:$(cat target/test-classpath.txt) \
 *      net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiSampleImageFetcher
 * }</pre>
 */
public class TaxiSampleImageFetcher {

	public static void main(String[] args) throws IOException {
		Path repoRoot = Paths.get("").toAbsolutePath();
		while (repoRoot != null && !java.nio.file.Files.isDirectory(repoRoot.resolve("test_images"))) {
			repoRoot = repoRoot.getParent();
		}
		if (repoRoot == null) {
			System.err.println("未找到仓库根目录（缺少 test_images/）");
			System.exit(1);
		}
		Path taxiDir = repoRoot.resolve("test_images/taxi");
		System.out.println("抓取出租车票样本 → " + taxiDir);
		List<Path> samples = BaiduSampleImageFetcher.fetchTaxiReceiptSamples(taxiDir);
		for (Path p : samples) {
			System.out.println("  " + p + " (" + java.nio.file.Files.size(p) + " bytes)");
		}
	}
}
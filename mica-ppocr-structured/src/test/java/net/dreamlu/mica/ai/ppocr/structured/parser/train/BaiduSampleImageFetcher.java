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

package net.dreamlu.mica.ai.ppocr.structured.parser.train;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度 OCR 演示页样本图抓取工具（test scope）。
 *
 * <p>仅用于火车票 / 出租车票等结构化解析器的端到端测试样本。
 *
 * <p>工作流程：
 * <ol>
 *   <li>GET 百度 OCR 演示页 HTML；</li>
 *   <li>正则提取 {@code img src="https://ai.bdstatic.com/..."}；</li>
 *   <li>下载图片到本地测试目录。</li>
 * </ol>
 *
 * <p><b>注意：</b>百度演示页样本图版权状态不明，仅供本仓库测试用途，
 * 切勿用于生产商业再分发。如对版权有顾虑，请使用自有样本图。
 *
 * <p>JDK 自带方案，无须引入额外依赖。
 */
public class BaiduSampleImageFetcher {

	/**
	 * 火车票演示页 URL。
	 */
	public static final String TRAIN_TICKET_PAGE = "https://ai.baidu.com/tech/ocr_receipts/train_ticket";

	/**
	 * 出租车票演示页 URL。
	 */
	public static final String TAXI_RECEIPT_PAGE = "https://ai.baidu.com/tech/ocr_receipts/taxi_receipt";

	/**
	 * ai.bdstatic.com 图片 URL 正则。
	 */
	private static final Pattern IMG_SRC_PATTERN = Pattern.compile(
		"<img[^>]*src=[\"'](https://ai\\.bdstatic\\.com/([^\"']+))[\"']", Pattern.CASE_INSENSITIVE);

	private BaiduSampleImageFetcher() {
	}

	/**
	 * 抓取并保存火车票演示页的所有样本图到指定目录。
	 *
	 * @param targetDir 目标目录（不存在会自动创建）
	 * @return 保存的文件路径列表
	 */
	public static List<Path> fetchTrainTicketSamples(Path targetDir) throws IOException {
		return fetchAndSave(TRAIN_TICKET_PAGE, targetDir, "train");
	}

	/**
	 * 抓取并保存出租车票演示页的所有样本图到指定目录。
	 *
	 * @param targetDir 目标目录（不存在会自动创建）
	 * @return 保存的文件路径列表
	 */
	public static List<Path> fetchTaxiReceiptSamples(Path targetDir) throws IOException {
		return fetchAndSave(TAXI_RECEIPT_PAGE, targetDir, "taxi");
	}

	private static List<Path> fetchAndSave(String pageUrl, Path targetDir, String prefix) throws IOException {
		Files.createDirectories(targetDir);
		String html = fetchHtml(pageUrl);
		List<String> urls = extractImageUrls(html);
		List<Path> saved = new ArrayList<>();
		int i = 0;
		java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
		for (String url : urls) {
			// 去重：百度页面同一图片资源可能被多个 img 标签引用
			String clean = url.split("\\?")[0];
			if (!seen.add(clean)) continue;
			i++;
			String filename = prefix + i + extensionOf(clean);
			Path target = targetDir.resolve(filename);
			download(url, target);
			saved.add(target);
		}
		return saved;
	}

	/**
	 * GET 页面 HTML（30 秒超时，伪装浏览器 UA）。
	 */
	static String fetchHtml(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(30_000);
			conn.setReadTimeout(30_000);
			conn.setRequestProperty("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			int code = conn.getResponseCode();
			if (code != 200) {
				throw new IOException("HTTP " + code + " when fetching " + url);
			}
			try (InputStream in = conn.getInputStream()) {
				return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
		} finally {
			conn.disconnect();
		}
	}

	/**
	 * 从 HTML 中提取 ai.bdstatic.com 图片 URL 列表。
	 */
	static List<String> extractImageUrls(String html) {
		List<String> urls = new ArrayList<>();
		Matcher m = IMG_SRC_PATTERN.matcher(html);
		while (m.find()) {
			urls.add(m.group(1));
		}
		return urls;
	}

	private static void download(String url, Path target) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(30_000);
			conn.setReadTimeout(30_000);
			conn.setRequestProperty("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
			int code = conn.getResponseCode();
			if (code != 200) {
				throw new IOException("HTTP " + code + " when downloading " + url);
			}
			try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
				Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			conn.disconnect();
		}
	}

	private static String extensionOf(String url) {
		int q = url.indexOf('?');
		String clean = q >= 0 ? url.substring(0, q) : url;
		int dot = clean.lastIndexOf('.');
		if (dot < 0 || dot < clean.lastIndexOf('/')) {
			return ".png";
		}
		String ext = clean.substring(dot).toLowerCase();
		// 百度静态资源通常是 png/jpg/jpeg；其它做兜底
		if (ext.length() > 5 || !ext.matches("\\.[a-z0-9]+")) {
			return ".png";
		}
		return ext;
	}

	/**
	 * 主入口（命令行调试用）：抓取火车票 + 出租车票样本图到 test_images/train/ / test_images/taxi/。
	 *
	 * <p>使用：
	 * <pre>{@code
	 * mvn -pl mica-ppocr-structured test-compile
	 * java -cp target/test-classes:target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) \
	 *      net.dreamlu.mica.ai.ppocr.structured.parser.train.BaiduSampleImageFetcher
	 * }</pre>
	 */
	public static void main(String[] args) throws IOException {
		Path repoRoot = Paths.get("").toAbsolutePath();
		while (repoRoot != null && !Files.isDirectory(repoRoot.resolve("test_images"))) {
			repoRoot = repoRoot.getParent();
		}
		if (repoRoot == null) {
			System.err.println("未找到仓库根目录（缺少 test_images/）");
			System.exit(1);
		}
		Path trainDir = repoRoot.resolve("test_images/train");
		Path taxiDir = repoRoot.resolve("test_images/taxi");
		System.out.println("抓取火车票样本 → " + trainDir);
		List<Path> trainSamples = fetchTrainTicketSamples(trainDir);
		for (Path p : trainSamples) {
			System.out.println("  " + p);
		}
		System.out.println("抓取出租车票样本 → " + taxiDir);
		List<Path> taxiSamples = fetchTaxiReceiptSamples(taxiDir);
		for (Path p : taxiSamples) {
			System.out.println("  " + p);
		}
	}
}
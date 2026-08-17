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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BaiduSampleImageFetcher} 单元测试。
 *
 * <p>仅测试纯文本解析能力；网络下载测试需要外网，跳过避免 CI 不稳定。
 */
class BaiduSampleImageFetcherTest {

	@Test
	void extractImageUrls_extractsAllBdstaticImages() {
		String html = "<div>"
			+ "<img src=\"https://ai.bdstatic.com/file/3BD61A17B91047F583BB86335AEC8431\" alt=\"火车票1\">"
			+ "<img src=\"https://ai.bdstatic.com/file/AD64049F8D654D6D9F61C3612B1F5620.png\" />"
			+ "<img src='https://ai.bdstatic.com/file/27C515D8C6C64C5E99796FA7B4453DF9.jpg' />"
			+ "<img src=\"https://other.cdn.com/foo.png\" />"
			+ "</div>";
		List<String> urls = BaiduSampleImageFetcher.extractImageUrls(html);
		assertEquals(3, urls.size());
		assertTrue(urls.get(0).startsWith("https://ai.bdstatic.com/file/3BD61A17B91047F583BB86335AEC8431"));
		assertTrue(urls.get(1).endsWith(".png"));
		assertTrue(urls.get(2).endsWith(".jpg"));
	}

	@Test
	void extractImageUrls_emptyHtmlReturnsEmpty() {
		List<String> urls = BaiduSampleImageFetcher.extractImageUrls("");
		assertTrue(urls.isEmpty());
	}

	@Test
	void extractImageUrls_ignoresOtherCdns() {
		String html = "<img src=\"https://example.com/1.png\">"
			+ "<img src=\"https://cdn.jsdelivr.net/2.png\">";
		List<String> urls = BaiduSampleImageFetcher.extractImageUrls(html);
		assertTrue(urls.isEmpty());
	}

	@Test
	void extractImageUrls_isCaseInsensitive() {
		String html = "<IMG SRC=\"HTTPS://AI.BDSTATIC.COM/FILE/ABC\" />";
		List<String> urls = BaiduSampleImageFetcher.extractImageUrls(html);
		assertFalse(urls.isEmpty());
	}
}
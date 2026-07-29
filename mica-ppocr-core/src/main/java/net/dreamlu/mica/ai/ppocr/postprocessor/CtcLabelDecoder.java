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

package net.dreamlu.mica.ai.ppocr.postprocessor;

import lombok.ToString;
import net.dreamlu.mica.ai.ppocr.utils.NdArrayUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CTC greedy decode：argmax → 去连续重复 → 去 blank → 查表出字。
 *
 * <p>对应 Python 端的 CTCLabelDecode.call()。
 */
@ToString
public final class CtcLabelDecoder {

	/** CTC blank 标签索引。 */
	public static final int BLANK = 0;

	private final String[] chars;

	/**
	 * 通过字符字典文件路径构造解码器。
	 *
	 * @param characterDictPath 字符字典文件路径
	 */
	public CtcLabelDecoder(String characterDictPath) {
		this(Path.of(characterDictPath));
	}

	/**
	 * 通过字符字典文件 Path 构造解码器。
	 *
	 * @param characterDictPath 字符字典文件
	 */
	public CtcLabelDecoder(Path characterDictPath) {
		if (!Files.isReadable(characterDictPath)) {
			throw new IllegalArgumentException(
				"字符字典不可读: " + characterDictPath.toAbsolutePath());
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(characterDictPath, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("读取字符字典失败: " + characterDictPath, e);
		}

		List<String> list = new ArrayList<>(lines.size() + 1);
		list.add("blank");
		for (String line : lines) {
			list.add(stripTrailing(line));
		}
		this.chars = list.toArray(new String[0]);
	}

	private static String stripTrailing(String s) {
		if (s == null) return "";
		int end = s.length();
		while (end > 0) {
			char c = s.charAt(end - 1);
			if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
				end--;
			} else {
				break;
			}
		}
		return s.substring(0, end);
	}

	/**
	 * 词表大小（包含 blank）。
	 *
	 * @return 字符字典大小
	 */
	public int vocabSize() {
		return chars.length;
	}

	/**
	 * 解码索引与概率。
	 *
	 * @param indices (B, T) int 索引
	 * @param probs   (B, T) float 概率，可为 null
	 * @return 解码结果
	 */
	public Result decode(int[][] indices, float[][] probs) {
		int b = indices.length;
		String[] texts = new String[b];
		float[] scores = new float[b];
		for (int i = 0; i < b; i++) {
			int[] seq = indices[i];
			int t = seq.length;
			boolean[] keep = new boolean[t];
			if (t > 0) {
				keep[0] = true;
				for (int j = 1; j < t; j++) {
					keep[j] = seq[j] != seq[j - 1];
				}
				for (int j = 0; j < t; j++) {
					if (seq[j] == BLANK) {
						keep[j] = false;
					}
				}
			}

			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < t; j++) {
				if (keep[j]) {
					int idx = seq[j];
					if (idx >= 0 && idx < chars.length) {
						sb.append(chars[idx]);
					}
				}
			}
			texts[i] = sb.toString();

			if (probs == null) {
				scores[i] = 1.0f;
			} else {
				float sum = 0f;
				int count = 0;
				for (int j = 0; j < t; j++) {
					if (keep[j]) {
						sum += probs[i][j];
						count++;
					}
				}
				scores[i] = count > 0 ? sum / count : 0.0f;
			}
		}
		return new Result(texts, scores);
	}

	/**
	 * 直接对模型输出 (B, T, C) 进行解码。
	 *
	 * @param modelOutput 模型输出张量
	 * @return 解码结果
	 */
	public Result call(float[][][] modelOutput) {
		int[][] indices = NdArrayUtils.argmaxLastAxis(modelOutput);
		float[][] probs = NdArrayUtils.maxLastAxis(modelOutput);
		return decode(indices, probs);
	}

	/**
	 * CTC 解码结果。
	 *
	 * @param texts  解码后的字符串数组
	 * @param scores 每条字符串的平均置信度
	 */
	public record Result(String[] texts, float[] scores) {}
}

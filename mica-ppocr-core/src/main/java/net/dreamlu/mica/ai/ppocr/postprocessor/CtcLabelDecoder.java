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
			// stripTrailing 是 Java 11+ 标准库方法，仅去掉尾部空白字符
			list.add(line == null ? "" : line.stripTrailing());
		}
		this.chars = list.toArray(new String[0]);
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
		int vocabSize = chars.length;
		for (int i = 0; i < b; i++) {
			int[] seq = indices[i];
			int t = seq.length;
			StringBuilder sb = new StringBuilder();
			float sum = 0f;
			int count = 0;
			// 单次扫描：同时处理 keep 判定 + 拼接 + 概率累加
			if (t > 0) {
				int prev = seq[0];
				boolean keep = (prev != BLANK);
				if (keep) {
					if (prev >= 0 && prev < vocabSize) {
						sb.append(chars[prev]);
					}
					if (probs != null) {
						sum += probs[i][0];
						count++;
					}
				}
				for (int j = 1; j < t; j++) {
					int cur = seq[j];
					boolean keepCur = (cur != prev) && (cur != BLANK);
					if (keepCur) {
						if (cur >= 0 && cur < vocabSize) {
							sb.append(chars[cur]);
						}
						if (probs != null) {
							sum += probs[i][j];
							count++;
						}
					}
					prev = cur;
				}
			}
			texts[i] = sb.toString();
			// probs == null 时按 1.0f 处理（保持向后兼容）
			scores[i] = (probs == null) ? 1.0f : (count > 0 ? sum / count : 0.0f);
		}
		return new Result(texts, scores);
	}

	/**
	 * 直接对模型输出 (B, T, C) 进行解码。
	 *
	 * <p>单次扫描：argmax + max + CTC 解码 一次完成，避免分配中间的 {@code int[][]}
	 * 和 {@code float[][]} 数组。
	 *
	 * @param modelOutput 模型输出张量
	 * @return 解码结果
	 */
	public Result call(float[][][] modelOutput) {
		int b = modelOutput.length;
		String[] texts = new String[b];
		float[] scores = new float[b];
		int vocabSize = chars.length;
		for (int i = 0; i < b; i++) {
			float[][] sequence = modelOutput[i];
			int t = sequence.length;
			StringBuilder sb = new StringBuilder();
			float sum = 0f;
			int count = 0;
			if (t > 0) {
				// 第 1 步：第 0 个时间步的 argmax + max
				float[] row0 = sequence[0];
				int bestIdx = 0;
				float bestVal = row0[0];
				for (int c = 1; c < row0.length; c++) {
					if (row0[c] > bestVal) {
						bestVal = row0[c];
						bestIdx = c;
					}
				}
				int prev = bestIdx;
				if (prev != BLANK) {
					if (prev >= 0 && prev < vocabSize) {
						sb.append(chars[prev]);
					}
					sum += bestVal;
					count++;
				}
				// 第 2 步起：单次扫描 + CTC 合并
				for (int j = 1; j < t; j++) {
					float[] row = sequence[j];
					int curIdx = 0;
					float curVal = row[0];
					for (int c = 1; c < row.length; c++) {
						if (row[c] > curVal) {
							curVal = row[c];
							curIdx = c;
						}
					}
					if (curIdx != prev && curIdx != BLANK) {
						if (curIdx >= 0 && curIdx < vocabSize) {
							sb.append(chars[curIdx]);
						}
						sum += curVal;
						count++;
					}
					prev = curIdx;
				}
			}
			texts[i] = sb.toString();
			scores[i] = count > 0 ? sum / count : 0.0f;
		}
		return new Result(texts, scores);
	}

	/**
	 * CTC 解码结果。
	 *
	 * @param texts  解码后的字符串数组
	 * @param scores 每条字符串的平均置信度
	 */
	public record Result(String[] texts, float[] scores) {}
}

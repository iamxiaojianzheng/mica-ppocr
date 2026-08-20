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

package net.dreamlu.mica.ai.ppocr.config;

import lombok.Builder;
import lombok.Getter;

/**
 * PP-OCRv6 引擎配置。
 *
 * <p>使用 Builder 模式构建，所有参数均有合理默认值。
 */
@Getter
@Builder
public final class PPOcrV6Config {

	/** 检测模型路径（必填） */
	private String detModelPath;

	/** 识别模型路径（必填） */
	private String recModelPath;

	/** 识别字符字典路径（必填） */
	private String recCharDictPath;

	/**
	 * 检测：限制边长（默认 960）。
	 *
	 * <p>PaddleX v4 / v5 / v6 官方推荐值。原默认值 64 是 PP-OCRv2/v3 时代"短边放大"
	 * 残留，对 4K/2K 大图会做大量无效缩放。960 + {@code detLimitType="max"} 是当前
	 * 官方推荐组合，长边 > 960 才缩放，精度无损、速度最优。
	 */
	@Builder.Default
	private int detLimitSideLen = 960;

	/**
	 * 检测：限制类型，min 或 max（默认 max）。
	 *
	 * <p>PaddleX v4 / v5 / v6 官方默认 max。原默认值 "min" 是 PP-OCRv2/v3 时代行为。
	 * 见 {@link #detLimitSideLen}。
	 */
	@Builder.Default
	private String detLimitType = "max";

	/** 检测：最大边长限制 */
	@Builder.Default
	private int detMaxSideLimit = 4000;

	/** 检测：DB 后处理二值化阈值 */
	@Builder.Default
	private float detThresh = 0.3f;

	/** 检测：DB 后处理 box 阈值 */
	@Builder.Default
	private float detBoxThresh = 0.6f;

	/** 检测：DB 后处理 unclip 比率 */
	@Builder.Default
	private float detUnclipRatio = 1.5f;

	/** 识别：输入图像形状 [C, H, W] */
	@Builder.Default
	private int[] recImageShape = {3, 48, 320};

	/** 识别：批处理大小 */
	@Builder.Default
	private int recBatchSize = 6;

	/** 是否优先使用 GPU 加速（默认 false，强制 CPU） */
	@Builder.Default
	private boolean preferAccelerator = false;

	/** 是否启用文档方向分类（PP-OCRv6 use_doc_orientation_classify，对应 PP-LCNet_x1_0_doc_ori） */
	@Builder.Default
	private boolean useDocOrientationClassify = false;

	/** 文档方向分类模型路径（useDocOrientationClassify=true 时必填） */
	private String docOrientationModelPath;

	/**
	 * 文档方向分类置信度阈值，低于此值视为 0°（不旋转）。范围 [0, 1]，默认 0.4。
	 *
	 * <p>采用 {@code 0.4} 作为经验阈值。取值依据（实测 doc_ori 模型的 4 类 softmax 概率）：
	 * <ul>
	 *   <li>idcard1（手机横拍、270° 倒置）：score=0.430 → 必须 ≥ 0.4 才能正确旋转</li>
	 *   <li>taxi1 / taxi3（正向图、doc_ori 误判 180°）：score=0.387/0.396 → 必须 > 0.4 才能丢弃</li>
	 *   <li>其它 taxi2/4/5、train1~5 全部 score &lt; 0.3，0.4 阈值也不会误触发</li>
	 * </ul>
	 *
	 * <p>{@code 0.4} 是当前样本集下"误判丢弃 / 误判旋转"的最佳折中点。
	 * 调高（如 0.5）会让 idcard1 类真实倒置图失去旋转机会；
	 * 调低（如 0.3）会让 taxi1/3 这种 doc_ori 弱信号被误触，反而把正向图转成 180°。
	 */
	@Builder.Default
	private float docOrientationThresh = 0.4f;

	/** ONNX Runtime 线程数 */
	@Builder.Default
	private int intraOpNumThreads = 1;

	/** ONNX Runtime 线程数 */
	@Builder.Default
	private int interOpNumThreads = 1;

	/**
	 * 返回使用全部默认字段的 PPOcrV6Config。
	 *
	 * @return 默认配置
	 */
	public static PPOcrV6Config defaults() {
		return builder().build();
	}
}

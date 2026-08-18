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

import lombok.Data;
import org.noear.solon.annotation.BindProps;
import org.noear.solon.annotation.Configuration;

/**
 * PP-OCR 配置属性。
 *
 * <p>对应 mica.ai.ppocr 配置前缀。
 */
@Data
@Configuration
@BindProps(prefix = "mica.ai.ppocr")
public class PPOCRProperties {

	/**
	 * 是否启用该 Starter。默认 true：启用时必填的 det/rec 模型路径及字典缺失将启动失败；
	 * 设为 false 时整个 Starter 不注入任何 Bean。
	 */
	private boolean enabled = true;

	/** 检测模型路径（必填） */
	private String detModelPath;

	/** 识别模型路径（必填） */
	private String recModelPath;

	/** 识别字符字典路径（必填） */
	private String recCharDictPath;

	/** 检测图像短边限制 */
	private int detLimitSideLen = 64;

	/** 检测限制类型: min / max */
	private String detLimitType = "min";

	/** 检测最大边长限制 */
	private int detMaxSideLimit = 4000;

	/** 检测阈值 */
	private float detThresh = 0.3f;

	/** 检测框阈值 */
	private float detBoxThresh = 0.6f;

	/** 检测 unclip 比例 */
	private float detUnclipRatio = 1.5f;

	/** 识别输入 shape [C, H, W] */
	private int[] recImageShape = {3, 48, 320};

	/** 识别批处理大小 */
	private int recBatchSize = 6;

	/** 是否优先使用 GPU 加速（默认 false，强制 CPU 保证跨平台 bit-exact） */
	private boolean preferAccelerator = false;

	/** 是否启用文档方向分类（PP-OCRv6 use_doc_orientation_classify，对应 PP-LCNet_x1_0_doc_ori） */
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
	private float docOrientationThresh = 0.4f;

	/** ONNX 内部线程数 */
	private int intraOpNumThreads = 1;

	/** ONNX 交互线程数 */
	private int interOpNumThreads = 1;
}

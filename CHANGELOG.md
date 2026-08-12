# 变更记录

## 发行版本

### v1.1.1 - 2026-08-12
- feat(ocr): 支持 PP-OCRv6 文档方向分类（use_doc_orientation_classify）。使用 PP-LCNet_x1_0_doc_ori 模型（4 类：0°/90°/180°/270°），在 OCR 检测前对整图做方向校正，避免用户侧倒拍/横拍导致识别失败。新增 PPOcrV6Config.useDocOrientationClassify / docOrientationModelPath / docOrientationThresh 配置项；PPOcrV6Engine.runMat 在检测前自动完成方向分类 + 旋转；行为完全向后兼容（默认关闭）。
- docs(readme): 补充 PP-OCRv6 文档方向分类（use_doc_orientation_classify）使用说明；§2 模型目录新增 doc_ori 可选模型；新增 §4.3 完整子章节（模型下载 / Java 代码 / Spring Boot yml / 性能代价 / 与弃用 use_angle_cls 的关系）。
- refactor(engine): PPOcrV6Engine 内部代码精简。`run(Path)` / `detect(Path)` 抽出 `loadMat(Path)` 私有方法消除 native-vs-fallback 重复；`closeSessions` 用 for 循环消除 3 段重复 try/catch；`runMat` 拆为"Mat 生命周期管理"和"核心流水线"两层，单层嵌套；`classifyAndRotateDocOrientation` 改用 switch 表达式。净减 12 行，行为完全不变。
- perf(core): 性能优化 + 修 native Mat 泄漏。DocOrientationPreprocessor 修 resizeShort 返回新 Mat 未 release 的泄漏（约 1.5 MB/调用，长期运行 OOM 风险）；CtcLabelDecoder 解码循环 3 合并为 1 次（call(float[][][]) 同步：argmax + max + CTC 单次扫描）；CtcLabelDecoder.stripTrailing 改用 Java 11+ String.stripTrailing；DbPostProcessor.boxScore 去冗余 float[][] 深拷贝；PPOcrTemplate 4×5 解析器便捷方法去中间跳转；9 个测试全部通过。
- fix(engine): 文档方向分类旋转方向修复。`use_doc_orientation_classify=true` 启用后，idcard1.jpg（顺时针 270° 旋转）旋转成 180° 而非 0°，导致 OCR 全 null。
  PaddleX doc_ori label 语义是"图片已顺时针旋转了 N 度"，摆正需逆向旋转同样的角度——原代码 `case 90 -> ROTATE_90_CLOCKWISE` / `case 270 -> ROTATE_90_COUNTERCLOCKWISE` 都写反了。
  修复后：身份证正面可正确识别（姓名/性别/民族/出生/住址/身份证号 全部解出）。
- refactor(config): docOrientationThresh 默认值 `0.5 → 0.3`。实测 doc_ori 在 4 类问题上经常给出 `[0.19, 0.19, 0.19, 0.43]` 这种"4 类接近随机"的分布，0.5 阈值会频繁触发降级丢失方向；0.3 是更实用的弱信号保留阈值。

### v1.1.0 - 2026-08-12
- feat(parser): 新增 mica-ppocr-structured 结构化解析模块，支持行驶证、身份证、银行卡、驾驶证 4 类证件；提供 SPI 接口 BaseStructuredParser 与公共骨架 LabelMatcher（标签定位 + 位置匹配 + 正则兜底）。
- feat(starter): 新增 PPOcrTemplate 一站式封装（mica-ppocr-spring-boot-starter），自动装配 PPOcrTemplate 与 4 个结构化解析器 Bean；提供纯 OCR 识别及 4 类证件结构化解析便捷方法。
- feat(core): 结构化结果支持可视化坐标，新增 BaseStructuredResult 抽象类，统一持有 rawResults（完整 OCR 原始框）与 fieldBoxes（字段→坐标列表映射）。
- refactor(core): 公开 API 去掉 Mat 入参；PPOcrV6Engine 的 run / detect 统一委托到 Path 版本（默认 FS 走 OpenCV native 读取，非默认 FS 自动回退 Files.readAllBytes → byte[]），新增 String / File / Path / byte[] / InputStream 5 种入参重载；原 Mat 版重命名为 runMat / detectMat / recognizeMat（public，标记为"已持有 Mat 复用"的高级场景，调用方负责 release）；内部统一 try-finally 释放 Mat，调用方无需任何 native 内存管理。

### v1.0.1 - 2026-08-10
- fix(core): 释放原生推理资源，避免本地句柄泄漏；新增泄漏回归测试并加固构造器清理路径。
- refactor(opencv): OpenCV 加载方法由 loadShared 改为 loadLocally

### v1.0.0 - 2026-08-07
- feat(core): 实现 PP-OCRv6 文字检测与识别核心功能（DB 后处理 + CTC 解码）。
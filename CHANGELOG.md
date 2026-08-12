# 变更记录

## 发行版本

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
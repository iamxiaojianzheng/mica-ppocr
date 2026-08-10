# 变更记录

## 发行版本

### v1.0.1 - 2026-08-10
- fix(core): 释放原生推理资源，避免本地句柄泄漏；新增泄漏回归测试并加固构造器清理路径。
- refactor(opencv): OpenCV 加载方法由 loadShared 改为 loadLocally

### v1.0.0 - 2026-08-07
- feat(core): 实现 PP-OCRv6 文字检测与识别核心功能（DB 后处理 + CTC 解码）。

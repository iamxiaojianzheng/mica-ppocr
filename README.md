# mica-ppocr（Java 图片 OCR 识别）

[![Java CI](https://github.com/lets-mica/mica-ppocr/actions/workflows/test-and-build.yml/badge.svg)](https://github.com/lets-mica/mica-ppocr/actions/workflows/test-and-build.yml)
![JAVA 17](https://img.shields.io/badge/JDK-17+-brightgreen.svg)
[![Mica Maven release](https://img.shields.io/maven-central/v/net.dreamlu/mica-ppocr-core.svg?style=flat-square)](https://central.sonatype.com/artifact/net.dreamlu/mica-ppocr-core/versions)

> PP-OCRv6 文字检测 + 识别的 **Java 17** 实现，纯 ONNX Runtime 推理，
> **零 PaddlePaddle 依赖**。完整复现预处理 / 后处理（DB 后处理、CTC 解码、
> pyclipper 等价的多边形 unclip）。

移植自 [`AIwork4me/ppocrv6_onnx`](https://github.com/AIwork4me/ppocrv6_onnx) 的 `ppocrv6_onnx.py` 单文件参考实现，**与 Python 版本保持 bit-exact**（默认 CPU 单线程）。

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Azul Zulu 17 / Temurin 17 / Oracle 17 |
| Maven | 3.6+ | 编译 / 打包 |
| ONNX Runtime | 1.26.0 | 由 Maven 自动拉取 |
| OpenCV | 4.10.0-0 | 由 Maven 自动拉取（含 Windows/Linux/macOS 原生库） |
| JTS | 1.20.0 | 多边形偏移（pyclipper 等价物） |

## 2. 模型目录

已经下载了 PP-OCRv6 官方 ONNX 模型（det + rec），放到 `models/ppocr-v6/{tier}/` 目录，可以按需选择：

```lua
models/ppocr-v6/
├── tiny/        # 轻量，速度快，精度一般 (det 1.7MB + rec 4.3MB)
│   ├── det.onnx
│   ├── rec.onnx
│   └── dict.txt
├── small/       # 平衡档 (det 9.4MB + rec 20.2MB)
│   ├── det.onnx
│   ├── rec.onnx
│   └── dict.txt
└── medium/      # 高精度档 (det 59.2MB + rec 73.0MB)
    ├── det.onnx.zip # 由于 git 限制，需要解压后使用
    ├── rec.onnx.zip # 由于 git 限制，需要解压后使用
    └── dict.txt
```

### 2.1 模型选择

| 档次 | det 模型 | rec 模型 | 字符表 | 定位 |
|------|----------|----------|--------|------|
| `tiny` | 1.7 MB | 4.3 MB | 约 2855 字符 | 轻量优先，速度快，精度一般 |
| `small` | 9.4 MB | 20.2 MB | 约 2855 字符 | 速度与精度均衡，推荐默认 |
| `medium` | 59.2 MB | 73.0 MB | 约 7180 字符 | 精度优先，覆盖更全字符集 |

### 2.2 模型场景

| 场景 | 推荐档次 | 说明 |
|------|---------|------|
| 移动端 / 嵌入式设备 | `tiny` | 模型体积小、内存占用低，适合资源受限环境 |
| 实时视频流 / 高并发调用 | `tiny` | 推理延迟低，吞吐高 |
| 通用文档识别、日常开发调试 | `small` | 各项指标均衡，覆盖大多数场景 |
| 服务器端批量识别 | `small` | 速度与精度兼顾，推荐默认 |
| 复杂版面 / 低质量图片 | `medium` | 检测与识别精度最高 |
| 生僻字及全字符集场景 | `medium` | 字符表约 7180 个，覆盖面更广 |

### 2.4 模型评分

![模型评分](docs/images/v6acc_opt.png)

## 3. 本地 main 测试

每个解析器都提供了对应的 `XxxMain` 调试入口（如行驶证 [VehicleLicenseMain](mica-ppocr-structured/src/test/java/net/dreamlu/mica/ai/ppocr/structured/parser/vehicle/VehicleLicenseMain.java)），**直接运行 main 方法**即可做 OCR 推理 + 结构化解析，便于快速验证。

```bash
mvn -pl mica-ppocr-structured test-compile
mvn -pl mica-ppocr-structured exec:java -Dexec.mainClass=net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseMain
# 或在 IDE 中直接 Run 'VehicleLicenseMain'
```

各 `XxxMain` 的源码中按需修改图片路径和可视化输出路径（例：行驶证）：

```java
private static final String TIER       = "tiny";                        // tiny / small / medium
private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";  // 待推理图片
private static final String VIS_PATH   = "test_images/vehicle/vis.png";       // 可视化输出；传 null 跳过
```

提供的测试图片（`test_images/{vehicle,idcard,driver,bankcard}/*.png`）：

| 证件 | 示例图片 | 说明 |
|------|---------|------|
| 行驶证 | `vehicle1.png` / `vehicle2.png` / `vehicle3.png` | 中文标签版式，鲁GH9P12 / 京CAA966 等 |
| 身份证 | `idcard` 目录 | 正反面自动判定 |
| 银行卡 | `bankcard1.png` ~ `bankcard5.png` | 不同银行版式 |
| 驾驶证 | `driver` 目录 | 正页结构化 |

### 3.1 识别效果示例

以 `test_images/vehicle/vehicle1.png` 为例（模型档次 `tiny`），识别结果可视化如下：

| 输入图片 | 识别结果可视化 |
|---------|---------------|
| ![vehicle1](test_images/vehicle/vehicle1.png) | ![vis](test_images/vehicle/vis.png) |

注意：测试的行驶证来源于网络，如有侵权，请联系删除。

**识别结果：**

```text
--- 行驶证结构化解析 ---
plateNo:      鲁GH9P12
owner:        盛瑞传动股份有限公司
vehicleType:  小型普通客车
vin:          LJ8F3D5H910700001
issueDate:    2018-02-24
```

结构化解析采用 `标签定位 + 位置匹配 + 正则兜底 + 版面布局兜底` 的多层策略（详见 [VehicleLicenseParser](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/vehicle/VehicleLicenseParser.java)）：

- 标签残缺片段（如「所有人」被拆成「所」+「人」）会被自动过滤，不再误选为值
- 中英文标签互为 fallback（如「所有人」缺失时尝试「Owner」）
- 仍失败时按版面结构兜底（所有人位于「车辆类型」值行下方、「住址」标签行上方）
- 日期 / VIN / 车牌支持子串抽取，容忍 OCR 把两个字段合成一个框

## 3.2 结构化解析（mica-ppocr-structured）

引入依赖：

```xml
<dependency>
    <groupId>net.dreamlu.mica.ai</groupId>
    <artifactId>mica-ppocr-structured</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

本模块把 OCR 识别出的散落文字框，按业务版面组织成结构化字段。
通用能力已下沉到 [`LabelMatcher`](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/core/LabelMatcher.java)：

- **标签定位**：按左侧标签找右侧值框，支持 OCR 残缺标签模糊匹配
- **位置匹配**：值框 x 起点必须在标签右边缘右侧、y 范围与标签重叠
- **正则兜底**：标签定位失败时按内容特征扫描全部结果

已实现的解析器：

| 解析器 | 包 | 状态 | 支持字段 |
|--------|----|------|---------|
| 行驶证 | `net.dreamlu.mica.ai.ppocr.structured.parser.vehicle` | ✅ 已实现 | 号牌号码、所有人、车辆类型、VIN、发证日期 |
| 身份证（正反面自动判定） | `…structured.parser.idcard` | ✅ 已实现 | 姓名、性别、民族、出生日期、住址、公民身份号码、签发机关、有效期限（优先判定反面，规避残片误判）|
| 银行卡 | `…structured.parser.bankcard` | ✅ 已实现 | 卡号、有效期、银行名称 |
| 机动车驾驶证 | `…structured.parser.driver` | ✅ 已实现 | 证号、姓名、性别、国籍、住址、出生日期、初次领证日期、准驾车型、签发机关、有效期限 |

调用示例（行驶证）：

```java
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseParser;

import java.util.List;

public class Demo {
    public static void main(String[] args) {
        List<PPOcrV6Result> ocrResults; // 来自 PPOcrV6Engine.run(...)
        VehicleLicenseResult license = VehicleLicenseParser.parse(ocrResults);
        System.out.println("车牌: " + license.getPlateNo());
        System.out.println("VIN:  " + license.getVin());
    }
}
```

如需通过 Spring 注入使用，每个解析器都提供 `INSTANCE` 单例 + `BaseStructuredParser<R>` 实例接口：

```java
@Bean
public BaseStructuredParser<VehicleLicenseResult> vehicleLicenseParser() {
    return VehicleLicenseParser.INSTANCE;
}
```

## 4. 使用

引入依赖：

```xml
<dependency>
    <groupId>net.dreamlu.mica.ai</groupId>
    <artifactId>mica-ppocr-core</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

核心 API：`net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine`，实现 `Closeable`，推荐 try-with-resources。

```java
import net.dreamlu.mica.ai.ppocr.config.PPOcrV6Config;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Engine;
import net.dreamlu.mica.ai.ppocr.utils.OrtProviders;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import nu.pattern.OpenCV;

import java.util.List;

public class Demo {
    public static void main(String[] args) {
        OpenCV.loadLocally();
        Mat img = Imgcodecs.imread("test.png");
        PPOcrV6Config config = PPOcrV6Config.builder()
            .detModelPath("models/ppocr-v6/tiny/det.onnx")
            .recModelPath("models/ppocr-v6/tiny/rec.onnx")
            .recCharDictPath("models/ppocr-v6/tiny/dict.txt")
            .build();
        try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
            List<PPOcrV6Result> results = engine.run(img);
            for (PPOcrV6Result r : results) {
                System.out.printf("%s  (%.3f)%n", r.text(), r.score());
            }
        }
    }
}
```

支持更多调参（DB 阈值、识别批大小、ORT 线程数、GPU 加速等），详见 [PPOcrV6Config.java](mica-ppocr-core/src/main/java/net/dreamlu/mica/ai/ppocr/config/PPOcrV6Config.java)。

## 5. Spring Boot Starter

引入依赖：

```xml
<dependency>
    <groupId>net.dreamlu.mica.ai</groupId>
    <artifactId>mica-ppocr-spring-boot-starter</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

`application.yml`：

```yaml
mica:
  ai:
    ppocr:
      enabled: true                       # 设为 false 关闭 Starter
      det-model-path: models/ppocr-v6/tiny/det.onnx
      rec-model-path: models/ppocr-v6/tiny/rec.onnx
      rec-char-dict-path: models/ppocr-v6/tiny/dict.txt
      # 其它可选：det-thresh / det-box-thresh / det-unclip-ratio /
      #         rec-batch-size / prefer-accelerator / intra-op-num-threads / ...
```

注入引擎即可使用：

```java
@Service
public class OcrService {
    private final PPOcrV6Engine engine;
    public OcrService(PPOcrV6Engine engine) { this.engine = engine; }

    public List<PPOcrV6Result> recognize(Mat image) {
        return engine.run(image);
    }
}
```

### 4.1 PPOCRPropertiesCustomizer

业务方可在 `mica.ai.ppocr` 之外对配置做旁路覆盖（环境变量 / 配置中心 / 路径解析等）：

```java
@Component
public class TierCustomizer implements PPOCRPropertiesCustomizer {
    @Override
    public void customize(PPOcrV6Config.PPOcrV6ConfigBuilder builder) {
        String tier = System.getenv("PPOCR_TIER");
        if ("small".equals(tier)) {
            builder.detModelPath("models/ppocr-v6/small/det.onnx")
                   .recModelPath("models/ppocr-v6/small/rec.onnx")
                   .recCharDictPath("models/ppocr-v6/small/dict.txt");
        }
    }
}
```

或函数式风格：

```java
@Bean
PPOCRPropertiesCustomizer tierEnvCustomizer() {
    return builder -> {
        String tier = System.getenv("PPOCR_TIER");
        if (tier != null) {
            builder.detModelPath("models/ppocr-v6/" + tier + "/det.onnx")
                   .recModelPath("models/ppocr-v6/" + tier + "/rec.onnx")
                   .recCharDictPath("models/ppocr-v6/" + tier + "/dict.txt");
        }
    };
}
```

Customizer 按 Spring 容器顺序生效；多个时建议用 `@Order` 显式控制。

## 6. 项目结构

```
mica-ppocr/                                       # 父 pom（packaging=pom）
├── pom.xml
├── README.md
├── CLAUDE.md
├── models/ppocr-v6/{tiny,small,medium}/          # 自备模型（不在仓库中）
├── mica-ppocr-core/
│   └── src/main/java/net/dreamlu/mica/ai/ppocr/
│       ├── engine/PPOcrV6Engine.java             # 唯一公开入口（Closeable）
│       ├── engine/PPOcrV6Result.java             # record (text, score, box)
│       ├── config/PPOcrV6Config.java             # Lombok @Builder 配置
│       ├── preprocessor/DetectionPreprocessor.java
│       ├── preprocessor/RecognitionPreprocessor.java
│       ├── postprocessor/DbPostProcessor.java
│       ├── postprocessor/CtcLabelDecoder.java
│       └── utils/
│           ├── BoxUtil.java                      # 框排序 + minAreaRect
│           ├── CropUtil.java                     # 透视裁剪
│           ├── Offset.java                       # JTS BufferOp（pyclipper 等价）
│           ├── NdArrayUtils.java                 # numpy 风格轻量子集
│           └── OrtProviders.java                 # ORT 执行提供者选择
├── mica-ppocr-structured/                        # 结构化解析模块
│   └── src/main/java/net/dreamlu/mica/ai/ppocr/structured/
│       ├── parser/core/
│       │   ├── BaseStructuredParser.java         # 解析器 SPI 接口
│       │   └── LabelMatcher.java                 # 标签定位 + 位置匹配 + 正则兜底 公共骨架
│       └── parser/
│           ├── vehicle/VehicleLicenseParser.java # 行驶证
│           ├── idcard/IdCardParser.java          # 身份证（正反面自动判定）
│           ├── bankcard/BankCardParser.java      # 银行卡
│           └── driver/DriverLicenseParser.java   # 机动车驾驶证
└── mica-ppocr-spring-boot-starter/
    └── src/main/java/net/dreamlu/mica/ai/ppocr/autoconfigure/
        ├── PPOCRAutoConfiguration.java
        ├── PPOCRProperties.java                  # @ConfigurationProperties("mica.ai.ppocr")
        ├── PPOCRPropertiesCustomizer.java        # 旁路覆盖 SPI
        └── OpenCVNativeLoader.java
```

## 6. 许可证

Apache License Version 2.0

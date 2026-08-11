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

| 组件 | 版本 | 说明                                      |
|------|------|-----------------------------------------|
| JDK | 17+ | java 环境                                 |
| ONNX Runtime | 1.26.0 | 由 Maven 自动拉取                            |
| OpenCV | 4.10.0-0 | 由 Maven 自动拉取（含 Windows/Linux/macOS 原生库） |
| JTS | 1.20.0 | 多边形偏移（pyclipper 等价物）                    |

## 2. 模型目录

下载 PP-OCRv6 官方 ONNX 模型（det + rec），放到 `models/ppocr-v6/{tier}/` 目录：

| 档次 | det 模型 | rec 模型 | 字符表 | 定位 |
|------|----------|----------|--------|------|
| `tiny` | 1.7 MB | 4.3 MB | 约 2855 字符 | 轻量优先，速度快，精度一般 |
| `small` | 9.4 MB | 20.2 MB | 约 2855 字符 | 速度与精度均衡，推荐默认 |
| `medium` | 59.2 MB | 73.0 MB | 约 7180 字符 | 精度优先，覆盖更全字符集 |

> `medium` 的 det/rec 模型为 `.onnx.zip`，需解压后使用。

![模型评分](docs/images/v6acc_opt.png)

## 3. 本地 main 测试

每个解析器都提供了对应的 `XxxMain` 调试入口（如行驶证 [VehicleLicenseMain](mica-ppocr-structured/src/test/java/net/dreamlu/mica/ai/ppocr/structured/parser/vehicle/VehicleLicenseMain.java)），**直接运行 main 方法**即可做 OCR 推理 + 结构化解析。

各 `XxxMain` 源码中修改图片路径即可切换测试图片：

```java
private static final String TIER       = "tiny";                              // tiny / small / medium
private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";  // 待推理图片
private static final String VIS_PATH   = "test_images/vehicle/vis.png";       // 可视化输出；传 null 跳过
```

### 3.1 识别效果示例

以 `test_images/vehicle/vehicle1.png` 为例（模型档次 `tiny`）：

| 输入图片 | 识别结果可视化 |
|---------|---------------|
| ![vehicle1](test_images/vehicle/vehicle1.png) | ![vis](test_images/vehicle/vis.png) |

```text
--- 行驶证结构化解析 ---
plateNo:      鲁GH9P12
owner:        盛瑞传动股份有限公司
vehicleType:  小型普通客车
vin:          LJ8F3D5H910700001
issueDate:    2018-02-24
```

注意：测试的行驶证来源于网络，如有侵权，请联系删除。

## 4. 结构化解析（mica-ppocr-structured）

引入依赖：

```xml
<dependency>
    <groupId>net.dreamlu.mica.ai</groupId>
    <artifactId>mica-ppocr-structured</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

已实现的解析器：

| 解析器 | 解析类名 | 支持字段 |
|--------|----------|---------|
| 行驶证 | `VehicleLicenseParser` | 号牌号码、所有人、车辆类型、VIN、发证日期 |
| 身份证（正反面自动判定） | `IdCardParser` | 姓名、性别、民族、出生日期、住址、公民身份号码、签发机关、有效期限 |
| 银行卡 | `BankCardParser` | 卡号、有效期、银行名称 |
| 机动车驾驶证 | `DriverLicenseParser` | 证号、姓名、性别、国籍、住址、出生日期、初次领证日期、准驾车型、签发机关、有效期限 |

通用能力下沉到 [`LabelMatcher`](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/core/LabelMatcher.java)：标签定位 + 位置匹配 + 正则兜底 + 版面布局兜底。调用示例（行驶证）：

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

## 5. 核心引擎使用（mica-ppocr-core）

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

## 6. Spring Boot Starter

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

业务方可通过 `PPOCRPropertiesCustomizer` 对配置做旁路覆盖（环境变量 / 配置中心等）：

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

## 7. 项目结构

```
mica-ppocr/                                       # 父 pom（packaging=pom）
├── mica-ppocr-core/                              # 核心引擎，零 Spring 依赖
│   └── engine/PPOcrV6Engine.java                 # 唯一公开入口（Closeable）
├── mica-ppocr-structured/                        # 结构化解析模块
│   └── parser/
│       ├── core/LabelMatcher.java                # 标签定位 + 位置匹配 + 正则兜底 公共骨架
│       ├── vehicle/VehicleLicenseParser.java     # 行驶证
│       ├── idcard/IdCardParser.java              # 身份证（正反面自动判定）
│       ├── bankcard/BankCardParser.java          # 银行卡
│       └── driver/DriverLicenseParser.java       # 机动车驾驶证
└── mica-ppocr-spring-boot-starter/               # Spring Boot 自动配置
```

## 8. 许可证

Apache License Version 2.0

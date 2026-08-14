# mica-ppocr（Java 图片 OCR 识别）

[![Java CI](https://github.com/lets-mica/mica-ppocr/actions/workflows/test-and-build.yml/badge.svg)](https://github.com/lets-mica/mica-ppocr/actions/workflows/test-and-build.yml)
![JAVA 17](https://img.shields.io/badge/JDK-17+-brightgreen.svg)
[![Mica Maven release](https://img.shields.io/maven-central/v/net.dreamlu/mica-ppocr-core.svg?style=flat-square)](https://central.sonatype.com/artifact/net.dreamlu/mica-ppocr-core/versions)
![Mica Maven SNAPSHOT](https://img.shields.io/maven-metadata/v?metadataUrl=https://central.sonatype.com/repository/maven-snapshots/net/dreamlu/mica-ppocr-core/maven-metadata.xml)

> - PP-OCRv6 文字检测 + 识别的 **Java 17** 实现，纯 ONNX Runtime 推理，
> - **零 PaddlePaddle 依赖**。完整复现预处理 / 后处理（DB 后处理、CTC 解码、
> - pyclipper 等价的多边形 unclip。
> - doc_ori 文档方向分类模型，支持（4 类：0°/90°/180°/270°）在检测前对整图做方向校正，避免用户侧倒拍/横拍导致识别失败。

移植自 [`AIwork4me/ppocrv6_onnx`](https://github.com/AIwork4me/ppocrv6_onnx) 的 `ppocrv6_onnx.py` 单文件参考实现，**与 Python 版本保持 bit-exact**（默认 CPU 单线程）。

---

## 1. 环境要求

| 组件 | 版本 | 说明                        |
|------|------|---------------------------|
| JDK | 17+ | java 环境                   |
| ONNX Runtime | 1.18.0 | 此版本内置的原生库可兼容更多操作系统版本      |
| OpenCV | 4.10.0-0 | 含 Windows/Linux/macOS 原生库 |
| JTS | 1.20.0 | 多边形偏移（pyclipper 等价物）      |

## 2. 模型目录

下载 PP-OCRv6 官方 ONNX 模型（det + rec），放到 `models/ppocr-v6/{tier}/` 目录：

| 档次 | det 模型 | rec 模型 | 字符表 | 定位 |
|------|----------|----------|--------|------|
| `tiny` | 1.7 MB | 4.3 MB | 约 2855 字符 | 轻量优先，速度快，精度一般 |
| `small` | 9.4 MB | 20.2 MB | 约 2855 字符 | 速度与精度均衡，推荐默认 |
| `medium` | 59.2 MB | 73.0 MB | 约 7180 字符 | 精度优先，覆盖更全字符集 |

> `medium` 的 det/rec 模型为 `.onnx.zip`，需解压后使用。

**可选**：文档方向分类模型 `models/ppocr-v6/doc_ori/doc_ori.onnx`，`PP-LCNet_x1_0_doc_ori` 的 ONNX 导出，6.47 MB。

![模型评分](docs/images/v6acc_opt.png)

## 3. 快速开始

先准备模型（详见 §2 模型目录），再选下面任一入口。

### 3.1 Spring Boot 入口（Controller 直用）

```java
@Autowired 
private PPOcrTemplate ppocr;

@PostMapping("/ocr/vehicle")
public VehicleLicenseResult vehicle(@RequestParam MultipartFile file) throws IOException {
    return ppocr.parseVehicleLicense(file.getBytes());  // 一行：检测 → 识别 → 结构化
}
```

完整 API、调参与进阶用法见 §4 / §5 / §6。

### 3.2 本地 main 调试

每个解析器都提供了对应的 `XxxMain` 调试入口（如 [VehicleLicenseMain](mica-ppocr-structured/src/test/java/net/dreamlu/mica/ai/ppocr/structured/parser/vehicle/VehicleLicenseMain.java)），**直接运行 main 方法**即可做 OCR 推理 + 结构化解析。修改源码顶部的常量即可切换图片：

```java
private static final String TIER       = "tiny";                              // tiny / small / medium
private static final String IMAGE_PATH = "test_images/vehicle/vehicle1.png";  // 待推理图片
private static final String VIS_PATH   = "test_images/vehicle/vis.png";       // 可视化输出；传 null 跳过
```

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

> 注意：测试的行驶证来源于网络，如有侵权，请联系删除。

---

## 4. 核心引擎（mica-ppocr-core）

引入依赖：

```xml
<dependency>
    <groupId>net.dreamlu</groupId>
    <artifactId>mica-ppocr-core</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

核心 API：[`PPOcrV6Engine`](mica-ppocr-core/src/main/java/net/dreamlu/mica/ai/ppocr/engine/PPOcrV6Engine.java)，实现 `Closeable`，推荐 try-with-resources。

### 4.1 公开方法

公开入口只暴露 `String` / `File` / `Path` / `byte[]` 4 种入参，内部自动完成 OpenCV `Mat` 的解码与 release，**调用方无需关心 native 内存**：

- `run(...)` — 完整 OCR：检测 → 排序 → 裁剪 → 识别
- `detect(...)` — 仅检测，返回 boxes + scores

> - `Path` 重载兼容非默认文件系统（如 ZIP / JIMFS / 内存 FS）：优先走 native 文件读取，不支持的 FileSystem 自动退回 `Files.readAllBytes`。
> - 确需复用已加载 `Mat` 的高级场景（如同一图跑多次推理），可使用 `runMat(Mat)` / `detectMat(Mat)` / `recognizeMat(List<Mat>)`（`Mat` 的 release 由调用方负责）。

### 4.2 完整示例

```java
public class Demo {
    public static void main(String[] args) {
        OpenCV.loadLocally();  // 首次启动时加载 native 库
        PPOcrV6Config config = PPOcrV6Config.builder()
            .detModelPath("models/ppocr-v6/tiny/det.onnx")
            .recModelPath("models/ppocr-v6/tiny/rec.onnx")
            .recCharDictPath("models/ppocr-v6/tiny/dict.txt")
            .useDocOrientationClassify(true)                                  // 可选：整图方向分类
            .docOrientationModelPath("models/ppocr-v6/doc_ori/doc_ori.onnx")  // 开启后必填
            .docOrientationThresh(0.3f)                                       // < 此值视为 0°；默认 0.3
            .build();
        try (PPOcrV6Engine engine = new PPOcrV6Engine(config)) {
            List<PPOcrV6Result> results = engine.run("test_images/vehicle/vehicle1.png");
            for (PPOcrV6Result r : results) {
                System.out.printf("%s  (%.3f)%n", r.text(), r.score());
            }
        }
    }
}
```

### 4.3 调参（PPOcrV6Config）

DB 阈值、识别批大小、ORT 线程数、GPU 加速等全部走 [`PPOcrV6Config`](mica-ppocr-core/src/main/java/net/dreamlu/mica/ai/ppocr/config/PPOcrV6Config.java) 的 Lombok `@Builder`。常用项见 §6.2 的 `application.yml`，字段一一对应（kebab-case ↔ camelCase）。

### 4.4 文档方向分类（use_doc_orientation_classify）

支持（4 类：0°/90°/180°/270°）在检测前对整图做方向校正，避免用户侧倒拍/横拍导致识别失败。

- **模型**：shape = `[1, 3, 224, 224]`，输出 `[1, 4]`（softmax + argmax）。可从 [ModelScope `farming789/pp-lcnet-doc-ori`](https://www.modelscope.cn/models/farming789/pp-lcnet-doc-ori) 直接下载 `model.onnx`，零 Paddle 依赖。
- **性能与代价**：CPU 单图多 ~3 ms；内存多 ~30 MB（加载第三个 ONNX session）。
- **默认行为**：关闭（`useDocOrientationClassify=false`），与原版完全 bit-exact，不影响现有调用。

---

## 5. 结构化解析（mica-ppocr-structured）

引入依赖（Starter 已传递依赖，使用 §6 Spring Boot 时无需再写）：

```xml
<dependency>
    <groupId>net.dreamlu</groupId>
    <artifactId>mica-ppocr-structured</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

### 5.1 已实现的解析器

| 解析器 | 解析类 | 结果类型 |
|--------|--------|----------|
| 行驶证 | `VehicleLicenseParser` | `VehicleLicenseResult` |
| 身份证（正反面自动判定） | `IdCardParser` | `IdCardResult` |
| 银行卡 | `BankCardParser` | `BankCardResult` |
| 机动车驾驶证 | `DriverLicenseParser` | `DriverLicenseResult` |
| 营业执照 | `BusinessLicenseParser` | `BusinessLicenseResult` |
| 增值税发票 | `InvoiceParser` | `InvoiceResult` |

每个解析器都提供**静态 `parse(List<PPOcrV6Result>)`**（拿到 OCR 结果后直接调）和 **SPI `parseResults(...)`**（与 `BaseStructuredParser<R>` 接口对齐，便于自定义）两种调用形式。

### 5.2 结果结构（BaseStructuredResult）

所有结构化结果统一继承 [`BaseStructuredResult`](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/core/BaseStructuredResult.java)，带两个**可视化友好的通用字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `rawResults` | `List<PPOcrV6Result>` | 完整原始 OCR 结果（每个文字框的文本/置信度/四角坐标），可直接在页面上绘制全部文字框 |
| `fieldBoxes` | `Map<String, List<int[][]>>` | 字段名 → 该字段对应的 OCR 框坐标列表（一个字段可能跨多个框），方便高亮"这个字段来自画面哪几块" |

> 仅行驶证完整填充 `fieldBoxes`；其他解析器只保证 `rawResults` 填充。

### 5.4 自定义解析器

通用能力下沉到 [`LabelMatcher`](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/core/LabelMatcher.java)：标签定位 + 位置匹配 + 正则兜底 + 版面布局兜底，并提供 `WithBox` 系列重载（返回 `LabeledMatch(value, box)`，便于解析器回填 `fieldBoxes`）。实现 [`BaseStructuredParser<R>`](mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/core/BaseStructuredParser.java) 接口即可挂载到 `PPOcrTemplate.parse(..., parser)` 上（见 §6.3）。

---

## 6. Spring Boot Starter

引入依赖（自动传递 `mica-ppocr-core` + `mica-ppocr-structured`）：

```xml
<dependency>
    <groupId>net.dreamlu</groupId>
    <artifactId>mica-ppocr-spring-boot-starter</artifactId>
    <version>${mica.ppocr.version}</version>
</dependency>
```

### 6.1 完整配置

```yaml
mica:
  ai:
    ppocr:
      # ===== 开关：设为 false 时整个 Starter 不注入任何 Bean =====
      # enabled: true                                         # 默认 true
      # ===== 必填：三个模型文件路径 =====
      det-model-path: models/ppocr-v6/tiny/det.onnx          # 检测模型
      rec-model-path: models/ppocr-v6/tiny/rec.onnx          # 识别模型
      rec-char-dict-path: models/ppocr-v6/tiny/dict.txt      # 识别字符字典
      # ===== 检测（DB 后处理）参数 =====
      # det-limit-side-len: 64                                # 检测图像短边限制
      # det-limit-type: min                                   # 限制类型：min / max
      # det-max-side-limit: 4000                              # 检测最大边长限制
      # det-thresh: 0.3                                       # 检测像素阈值
      # det-box-thresh: 0.6                                   # 检测框阈值
      # det-unclip-ratio: 1.5                                 # 多边形 unclip 比例
      # ===== 识别参数 =====
      # rec-image-shape: [3, 48, 320]                         # 识别输入 shape [C, H, W]
      # rec-batch-size: 6                                     # 识别批处理大小
      # ===== 可选：文档方向分类（PP-LCNet_x1_0_doc_ori）=====
      # use-doc-orientation-classify: false                   # 是否启用整图 4 方向分类 + 自动旋转
      # doc-orientation-model-path: models/ppocr-v6/doc_ori/doc_ori.onnx  # 启用时必填
      # doc-orientation-thresh: 0.3                           # 置信度阈值 < 此值视为 0°；实测 0.3 比 0.5 更稳
      # ===== 性能 / 运行模式 =====
      # prefer-accelerator: false                             # 是否优先 GPU（默认 false 强制 CPU，保证 bit-exact）
      # intra-op-num-threads: 1                               # ONNX 内部线程数
      # inter-op-num-threads: 1                               # ONNX 交互线程数
```

### 6.2 PPOcrTemplate API

直接注入 [`PPOcrTemplate`](mica-ppocr-spring-boot-starter/src/main/java/net/dreamlu/mica/ai/ppocr/autoconfigure/PPOcrTemplate.java) 即可使用，内部已持有 Engine + 6 个证件解析器，无需手工装配。

**每个方法提供 5 种入参重载**：`String` 路径 / `File` / `Path` / `byte[]` / `InputStream`，典型场景：

| 入参 | 场景 |
|------|------|
| `String` | 本地文件路径 |
| `File` | `File` 对象 |
| `Path` | 非默认文件系统（ZIP / JIMFS / 内存 FS） |
| `byte[]` | Spring `MultipartFile.getBytes()` |
| `InputStream` | URL / S3 / HTTP 下载流 |

方法清单（每行都是 **× 5 种入参**）：

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `run(...)` | `List<PPOcrV6Result>` | 纯 OCR 识别（返回散落文字框） |
| `parse(..., parser)` | `R` | 通用结构化解析（传入自定义解析器，见 §5.4） |
| `parseVehicleLicense(...)` | `VehicleLicenseResult` | 行驶证 |
| `parseIdCard(...)` | `IdCardResult` | 身份证（正反面自动判定） |
| `parseBankCard(...)` | `BankCardResult` | 银行卡 |
| `parseDriverLicense(...)` | `DriverLicenseResult` | 驾驶证 |
| `parseBusinessLicense(...)` | `BusinessLicenseResult` | 营业执照 |
| `parseInvoice(...)` | `InvoiceResult` | 增值税发票 |

### 6.3 典型用法

```java
@Service
public class OcrService {
    @Autowired
    private PPOcrTemplate ppocr;

    // Spring Boot 上传（最常用）
    public VehicleLicenseResult recognizeVehicle(MultipartFile file) throws IOException {
        return ppocr.parseVehicleLicense(file.getBytes());
    }

    // 网络流 / S3 下载流
    public DriverLicenseResult recognizeDriver(URL url) throws IOException {
        try (InputStream in = url.openStream()) {
            return ppocr.parseDriverLicense(in);
        }
    }

    // 自定义解析器场景（任何入参都支持）
    public <R> R recognize(String imagePath, BaseStructuredParser<R> parser) {
        return ppocr.parse(imagePath, parser);
    }
}
```

### 6.4 可视化（rawResults + fieldBoxes）

```java
VehicleLicenseResult r = ppocr.parseVehicleLicense(file.getBytes());

// 画所有文字框（绿线）
for (PPOcrV6Result ocr : r.getRawResults()) {
    drawPolyline(ocr.box(), Color.GREEN);
}
// 高亮车牌字段（红线）
List<int[][]> plateBoxes = r.getFieldBoxes().get("plateNo");
if (plateBoxes != null) {
    for (int[][] box : plateBoxes) drawPolyline(box, Color.RED);
}
```

### 6.5 配置覆盖（环境变量 / 配置中心）

业务方可通过 `PPOCRPropertiesCustomizer` 对配置做旁路覆盖，常用于按环境切换 `tiny / small / medium`：

```java
@Bean
public PPOCRPropertiesCustomizer tierEnvCustomizer() {
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

## 7. 许可证

Apache License Version 2.0

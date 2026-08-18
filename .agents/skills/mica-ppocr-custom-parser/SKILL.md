---
name: mica-ppocr-custom-parser
description: >-
  在 mica-ppocr 项目中新增自定义结构化解析器（证件 / 票据 / 卡证 OCR → 业务字段）时加载本 skill。
  覆盖从 `BaseStructuredParser<R>` / `BaseStructuredResult` 继承、`LabelMatcher` 公共工具调用、
  Spring Boot 自动配置注册、`PPOcrTemplate` 集成，到 `BaseTest` 可视化调试与单测的完整链路。
  触发场景：用户说"加个 XX 证件 / 票据解析器"、"自定义结构化解析"、"怎么把 OCR 散落文字组织成字段"、
  "新增一种证件 OCR"、"写个新的 parser / 解析器"、"新加一个卡证/营业执照/发票/收据 等结构化识别"、
  "mica-ppocr 加新解析"、"解析器模板/模板解析"、"label-value 提取"。
---

# mica-ppocr 自定义结构化解析器

> 把 PP-OCRv6 检测出的散落文字框（`List<PPOcrV6Result>`），按"标签定位 + 位置匹配 + 正则兜底"的策略，
> 组织成业务字段对象（继承 `BaseStructuredResult` 的 POJO）。

模块路径：`mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/`

---

## 1. 何时用本 skill

用户提出以下任何需求时,先加载本 skill：

- "加一个 XX 证件 / 票据 / 卡证的结构化解析"
- "新加一个 parser,识别 XX 业务字段"
- "把 OCR 散落文字组织成业务字段"
- "在 mica-ppocr 里写个自定义解析器"
- 看到 `mica-ppocr-structured/.../parser/<new-biz>/` 这样的目录或被要求"参考 VehicleLicenseParser"

**不要**用本 skill 处理：

- 纯 OCR 推理（不改字段）→ 走 `PPOcrV6Engine` 即可,不需要结构化层
- 模型训练/字典扩充 → 走 PP-OCRv6 模型侧
- 非 mica-ppocr 项目 → 不适用

---

## 2. 三层骨架先认清楚

| 层 | 文件 | 职责 |
|---|---|---|
| **基类（必须继承）** | `core/BaseStructuredParser<R>` | 持有 `PPOcrV6Engine`,5 个 `parse(...)` 一站式重载已 `final` 实现；子类只覆盖 `parseResults(List<PPOcrV6Result>)` |
| **结果基类（必须继承）** | `core/BaseStructuredResult` | 提供 `rawResults`（原始 OCR 框）与 `fieldBoxes`（字段名 → 框坐标）,Lombok `@Data` |
| **公共工具（按需调用）** | `core/LabelMatcher` | 标签定位、位置匹配、正则兜底、合并框剥值、跨行拼接、互斥分配、几何工具 `minX/maxX/minY/maxY` |

`LabelMatcher` 是 `@UtilityClass` 静态方法集合,**不绑定任何具体业务**,所有解析器共享同一套语义。

---

## 3. 新增一个解析器,5 步走

### 3.1 创建包

在 `mica-ppocr-structured/src/main/java/net/dreamlu/mica/ai/ppocr/structured/parser/<biz>/` 下新增 2~3 个文件：

```
<biz>/
├── <Biz>Parser.java        # 继承 BaseStructuredParser<<Biz>Result>
├── <Biz>Result.java        # 继承 BaseStructuredResult
└── <Biz>Side.java          # 可选:有正反面/多版面时
```

**包名小写 + 业务名**（参考 `vehicle/`、`idcard/`、`invoice/`、`train/`）。

### 3.2 写 Result

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class InvoiceResult extends BaseStructuredResult {
    private String invoiceCode;     // 发票代码
    private String invoiceNo;       // 发票号码
    private String invoiceDate;     // 开票日期
    private BigDecimal amount;      // 价税合计
    // ... 业务字段
}
```

要点：

- 必须 `@Data` + `@EqualsAndHashCode(callSuper = true)` —— `rawResults` 和 `fieldBoxes` 在父类
- 字段名（key）要和后续 `LabelMatcher.applyFieldBox(result, "fieldName", match)` 一致
- 业务字段值允许为 `null`（OCR 失败/字段缺失）,不要用基本类型

### 3.3 写 Parser（核心）

```java
@Slf4j
public class InvoiceParser extends BaseStructuredParser<InvoiceResult> {

    // 1) 正则常量（业务相关）
    private static final Pattern INVOICE_CODE_PATTERN = Pattern.compile("^\\d{8,12}$");

    public InvoiceParser(PPOcrV6Engine engine) {
        super(engine);  // engine 可为 null:仅当只调 parseResults(List) 时
    }

    @Override
    public InvoiceResult parseResults(List<PPOcrV6Result> results) {
        InvoiceResult r = new InvoiceResult();
        r.setRawResults(new ArrayList<>(results));  // 必填:供调用方做可视化

        // 2) 逐字段填值
        LabeledMatch codeMatch = parseInvoiceCode(results);
        r.setInvoiceCode(codeMatch.value());
        LabelMatcher.applyFieldBox(r, "invoiceCode", codeMatch);

        // ... 其他字段
        return r;
    }
}
```

**模板四件套**：

1. 构造器接 `PPOcrV6Engine`,转给 `super(engine)`
2. `parseResults` 入口：new Result → `setRawResults(new ArrayList<>(results))` → 填字段 → return
3. 每个字段返回 `LabeledMatch`(值 + 匹配框),用 `LabelMatcher.applyFieldBox` 回填 `fieldBoxes`
4. 解析失败/未命中 → 字段值允许 null,打 `log.warn`(不要 `System.out.println`)

### 3.4 选 LabelMatcher 模式（按业务复杂度）

| 场景 | 推荐方法 | 备注 |
|---|---|---|
| 标准左标签 + 右值 | `matchValueWithBox` / `matchValue` | 默认走"右侧 y 重叠 + 最左"策略 |
| 标签与值合并到同一 OCR 框（"发票代码12345678"） | `matchValueFromPrefix` / `matchValueFromPrefixWithBox` | 自动从合并框剥前缀 |
| 标签定位后,值要按正则再校验 + 不匹配时回退正则 | `labelOrFallback` / `labelOrFallbackWithBox` | `fieldName` 用于日志,`last` 控制首/末匹配 |
| OCR 残缺标签（"号牌号"少了"码"） | `findLabelBox` 内部已支持,无需特别调用 | 完整等于 > 开头匹配 > 包含 fragment |
| 票据类有合并框需要从文本中抠值 | `matchSubstring` / `matchSubstringWithBox` | 传 `text -> 提取函数` |
| 标签被 OCR 切碎成 fragment（"日期"→ "日"） | `matchValueByLabelKeywordWithBox` | 传关键字列表 |
| 多个 label 抢同一右侧值（"金额/总金额"同行） | `assignExclusiveValues` | 贪心最佳优先互斥分配 |
| 跨多行的字段（住址、经营范围） | `collectMultiLineRight` | 按 y 升序拼接右侧 y 重叠框 |
| 几何位置兜底（无标签场景） | `minX/maxX/minY/maxY` | 自己写规则（参考 `BankCardParser#parseBankName`） |

**返回风格选择**：

- 老代码/简单场景 → `matchValue(...) -> String`（一行搞定）
- 新代码/需要回填 `fieldBoxes` → `matchValueWithBox(...) -> LabeledMatch`,后续 `LabelMatcher.applyFieldBox(result, "fieldName", match)`

### 3.5 注册到 Spring Boot（如果用户用了 starter）

在 `mica-ppocr-spring-boot-starter/src/main/java/net/dreamlu/mica/ai/ppocr/autoconfigure/StructuredParserAutoConfiguration.java`：

1. 新增 `@Bean`（参考现有 8 个）
2. 把它加到 `ppocrTemplate(...)` 方法签名 + 构造器调用 + null 校验里
3. 在 `PPOcrTemplate` 同步新增：字段、`@Getter` 方法、构造器参数、null 校验

**整套有 4 个文件要改**（只要用户走 Spring Boot）：

| 文件 | 改动 |
|---|---|
| `StructuredParserAutoConfiguration.java` | 新 `@Bean`,加进 `ppocrTemplate` 签名 |
| `PPOcrTemplate.java` | 新字段 + getter + 构造器参数 + null 校验 |
| 解析器 `Parser.java` | 新文件 |
| 解析器 `Result.java` | 新文件 |

**Solon / 非 Spring 用户**只需前 2 个文件,自行 `new XxxParser(engine)` 即可。

---

## 4. 7 个常用模式（按场景选）

### 模式 A：标准左标签 + 右值（行驶证、驾驶证、营业执照）

参考：`VehicleLicenseParser`、`DriverLicenseParser`、`BusinessLicenseParser`

```java
LabeledMatch m = LabelMatcher.matchValueWithBox(results, "号牌号码");
r.setPlateNo(m.value());
LabelMatcher.applyFieldBox(r, "plateNo", m);
```

### 模式 B：标签 + 正则兜底（身份证号、发票号、车牌）

参考：`VehicleLicenseParser` 的 plateNo/vin/issueDate

```java
LabeledMatch m = LabelMatcher.labelOrFallbackWithBox(
    LabelMatcher.matchValueWithBox(results, "车辆识别代号"),
    results, VIN_PATTERN, "VIN", false /* last=false 取首个 */);
```

### 模式 C：纯正则兜底（无标签,卡号、手机号）

参考：`BankCardParser#parseCardNumber`、`BankCardParser#parseHolderName`

```java
String cardNo = LabelMatcher.matchPattern(results, CARD_NUMBER_PATTERN, false);
```

### 模式 D：合并框剥值（OCR 把标签和值识别成一框）

参考：`VehicleLicenseParser#parseIdNumber`、`InvoiceParser#findInvoiceCode`

- 合并框 "公民身份号码362503..." → 用 `matchValueFromPrefix`
- 合并框 "No14641426" → 自己写 extractor：

```java
String no = LabelMatcher.matchSubstring(results, text -> {
    if (!text.startsWith("No")) return null;
    Matcher m = INVOICE_NO_PATTERN.matcher(text.substring(2));
    return m.find() ? m.group() : null;
});
```

### 模式 E：跨行字段（住址、经营范围、备注）

参考：`IdCardParser#parseAddress`、`LabelMatcher.collectMultiLineRight`

要点：合并框首行 + 后续 y 重叠右侧框按 y 升序拼接,中间空格分隔,最后用 `replaceAll("\\s+", "")` 去噪。

### 模式 F：版面/区域判定（身份证正反面、发票购销方）

参考：`IdCardParser#detectSide`、`InvoiceParser#parseParty`（用 `imgMidY` 分上下半区）

```java
// 1) 用特征标签判定（先反后正:反面字少,OCR 不易误识）
boolean isBack = LabelMatcher.findLabelBox(results, "签发机关") != null;
if (isBack) { ... }

// 2) 用 y 中位数分上下区
int imgMidY = computeImageMidY(results);
```

### 模式 G：多 label 互斥分配（金额行多 label 抢同一值）

参考：`LabelMatcher.assignExclusiveValues`、`LabelMatcher.LabelDef`

适用于"金额/总金额/小计"等 label 同行且都指向同一右侧值。

---

## 5. 必须遵守的约定

### 命名

- 包名：单数业务名（`vehicle` / `idcard` / `invoice` / `train` / `taxi` / `bankcard` / `driver` / `business`）
- 类名：`<业务名 PascalCase>Parser` / `<业务名 PascalCase>Result` / `IdCardSide`（多版面时）
- 字段名：业务术语,首字母小写驼峰（`plateNo` / `vin` / `issueDate` / `invoiceCode`）

### 日志

- 命中分支打 `log.debug`(不要 info,正常路径不打日志)
- 失败/兜底打 `log.warn`(`"身份证解析:未匹配到身份证号"`)
- 不要用 `System.out.println` / `System.err.println`

### 防御性

- `LabelMatcher.matchValueWithBox` 返回的可能是 `LabeledMatch.textOnly(null)`,**先判 `hasValue()`** 再用
- 正则匹配优先 `find()`(应对合并框),全等校验才用 `matches()`
- `r.setRawResults(new ArrayList<>(results))` —— **必须**用 new ArrayList 包一层,避免外部修改影响

### 不可做

- ❌ 不要覆盖 `BaseStructuredParser#parse(...)`(已 `final`)
- ❌ 不要让 `engine` 在 `parse(...)` 调用时为 null（基类会 NPE）
- ❌ 不要把 `LabelMatcher` 改成实例类（它是 `@UtilityClass`）
- ❌ 不要在 Result 用基本类型（int → Integer,BigDecimal 可以,double 不行）
- ❌ 不要在 Result 字段上加业务校验注解（保持 POJO 干净,校验放业务层）

### License header

每个新文件第一行要带：

```java
/*
 * Copyright (c) 2019-2026, dreamlu.net All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ... (省略,完整模板见项目其它文件)
 */
```

---

## 6. 调试 / 测试脚手架

### 6.1 写一个 `BaseTest` 子类（30 行跑通 demo）

参考 `src/test/java/.../parser/vehicle/VehicleLicenseMain.java`：

```java
public class MyBizMain extends BaseTest<MyBizParser, MyBizResult> {

    private static final String IMAGE_PATH = "test_images/mybiz/sample1.png";
    private static final String VIS_PATH   = "test_images/mybiz/vis.png";

    public static void main(String[] args) {
        new MyBizMain().demo(IMAGE_PATH, VIS_PATH);
    }

    @Override protected MyBizParser newParser(PPOcrV6Engine engine) {
        return new MyBizParser(engine);
    }

    @Override protected void printResult(MyBizResult r) {
        System.out.println("fieldA: " + r.getFieldA());
        // ...
    }
}
```

跑 demo：直接 IDE 跑 `main`,会打印所有 OCR 框 + 解析结果 + 可视化 PNG。

### 6.2 写 JUnit 单测（不依赖模型）

参考 `src/test/java/.../parser/core/LabelMatcherTest.java`、各 `ParserTest.java`：

- 用 mock 构造 `List<PPOcrV6Result>`（框坐标 + 文本 + score）
- 验证 `parseResults` 返回的字段值
- 不需要真模型,跑得快,CI 友好

### 6.3 调试技巧

- `logback-test.xml` 设 `level=DEBUG`,看 `LabelMatcher` 的 `[DEBUG-FIND]` / `结构化解析:` 日志
- `result.getRawResults()` 在测试里 dump 出来,人工对照看每个框
- `result.getFieldBoxes()` 在 demo 里画框,验证框是否落在正确字段上

---

## 7. 完整最小可运行示例

```java
// MyBizResult.java
@Data
@EqualsAndHashCode(callSuper = true)
public class MyBizResult extends BaseStructuredResult {
    private String title;     // 业务标题
    private String code;      // 业务编码
    private String date;      // 业务日期
}

// MyBizParser.java
@Slf4j
public class MyBizParser extends BaseStructuredParser<MyBizResult> {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z]{2}\\d{6}");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public MyBizParser(PPOcrV6Engine engine) {
        super(engine);
    }

    @Override
    public MyBizResult parseResults(List<PPOcrV6Result> results) {
        MyBizResult r = new MyBizResult();
        r.setRawResults(new ArrayList<>(results));

        // 标题:纯标签
        LabeledMatch title = LabelMatcher.matchValueWithBox(results, "标题");
        r.setTitle(title.value());
        LabelMatcher.applyFieldBox(r, "title", title);

        // 编码:标签 + 正则兜底
        LabeledMatch code = LabelMatcher.labelOrFallbackWithBox(
            LabelMatcher.matchValueWithBox(results, "编码"),
            results, CODE_PATTERN, "编码", false);
        r.setCode(code.value());
        LabelMatcher.applyFieldBox(r, "code", code);

        // 日期:正则兜底
        r.setDate(LabelMatcher.matchPattern(results, DATE_PATTERN, false));

        return r;
    }
}
```

启动期注册到 Spring Boot → 见 §3.5。

---

## 8. 现有参考实现索引（按复杂度排序）

| 解析器 | 难度 | 演示模式 | 关键技巧 |
|---|---|---|---|
| `BankCardParser` | ⭐ | 纯正则 + 位置兜底 | 英文标签黑名单、底部 y 过滤、卡号去空格 |
| `DriverLicenseParser` | ⭐⭐ | 标准左标签 + 右值 | 驾照字段比行驶证多一项 |
| `VehicleLicenseParser` | ⭐⭐ | 标准 + 兜底链 | labelOrFallback + 子串搜索 + 版面布局兜底 |
| `IdCardParser` | ⭐⭐⭐ | 双版面 + 合并框 | detectSide、cutAtNextLabel、跨行地址 |
| `BusinessLicenseParser` | ⭐⭐⭐ | 多行字段 + 区域过滤 | collectMultiLineRight、findCleanLabelBox |
| `TaxiReceiptParser` | ⭐⭐⭐⭐ | 票据版式碎片化 | keyword 定位、底部正则兜底、金额行多 label 互斥 |
| `TrainTicketParser` | ⭐⭐⭐⭐ | 票据版式碎片化 | 合并框切日期+时间、票号正则、噪声黑名单 |
| `InvoiceParser` | ⭐⭐⭐⭐⭐ | 复杂版面（购销双方/明细表/合计） | y 中位数分上下、fragment + 续段合并框、4 字段同模板 |

> 入门先看 `BankCardParser`；做卡证看 `VehicleLicenseParser` / `IdCardParser`；做票据看 `TrainTicketParser` / `TaxiReceiptParser`；做发票看 `InvoiceParser`。

---

## 9. 反模式 / 常见坑

| 坑 | 后果 | 怎么避 |
|---|---|---|
| 直接 `r.text().equals(label)` 找标签 | OCR 残缺（"号牌号"缺"码"）会漏匹配 | 用 `LabelMatcher.findLabelBox`(已支持 fragment) |
| 自己遍历 `r.text()` 写最近距离匹配 | 同行多个 label 都选到同一值 | 金额/票号类用 `assignExclusiveValues` |
| 跨行字段只取首个 y 重叠框 | 漏掉第二/三行 | 用 `collectMultiLineRight` 或自己写 y 升序拼接 |
| 用 `r.text().matches()` 匹配合并框 | 整框不匹配 → 漏识别 | 改用 `find()` 或写 extractor |
| Result 字段没加 `@EqualsAndHashCode(callSuper = true)` | Lombok 不生成 `equals` 用父类,反序列化可能丢 `rawResults` | 必加 |
| 忘记 `r.setRawResults(...)` | 调用方拿到空 `rawResults`,无法做可视化 | 入口第一行就 set |
| 兜底分支没打 log | 调试时不知道走了哪条路径 | 每个兜底命中打 `log.debug`,失败打 `log.warn` |
| Spring 注册时漏改 `PPOcrTemplate` | 启动报 `BankCardParser must not be null` | 4 个文件同步改（autoConfig + Template + Parser + Result） |

---

## 10. 必读源码

- `core/BaseStructuredParser.java` —— SPI 基类,5 个 `parse(...)` 重载 + `parseResults` 抽象方法
- `core/BaseStructuredResult.java` —— `rawResults` + `fieldBoxes` 通用能力
- `core/LabelMatcher.java` —— 700+ 行工具,涵盖 12+ 种匹配场景,**写解析器前先扫一遍注释找匹配场景**

写代码时 `LabelMatcher` 的 JavaDoc 是最权威的 API 文档,优先看它而不是猜。

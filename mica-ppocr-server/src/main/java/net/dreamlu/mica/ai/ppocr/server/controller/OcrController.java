package net.dreamlu.mica.ai.ppocr.server.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.dreamlu.mica.ai.ppocr.autoconfigure.PPOcrTemplate;
import net.dreamlu.mica.ai.ppocr.engine.PPOcrV6Result;
import net.dreamlu.mica.ai.ppocr.structured.parser.bankcard.BankCardResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.business.BusinessLicenseResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.driver.DriverLicenseResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.household.HouseholdRegisterResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.idcard.IdCardResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.invoice.InvoiceResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.taxi.TaxiReceiptResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.train.TrainTicketResult;
import net.dreamlu.mica.ai.ppocr.structured.parser.vehicle.VehicleLicenseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OCR 识别服务 REST 接口（全量包含通用文本检测与 9 种卡证票据结构化解析）
 *
 * @author Antigravity
 */
@Tag(name = "OCR 文字识别与结构化 API", description = "提供通用 OCR 散落文本框识别及各类卡证、票据的结构化字段抽取")
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    @Autowired
    private PPOcrTemplate ppocr;

    /**
     * 健康检查 / 服务状态探针
     */
    @Operation(summary = "服务健康检查探针", description = "用于 Docker 容器健康探针或微服务存活检测")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "UP");
        res.put("service", "mica-ppocr-server");
        res.put("timestamp", System.currentTimeMillis());
        return res;
    }

    /**
     * 通用文字 OCR 识别（返回图片中所有散落文本框及概率坐标）
     */
    @Operation(summary = "通用文字 OCR 识别", description = "上传图片文件，返回图中识别出的所有文本内容、置信度以及四角多边形坐标框")
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<PPOcrV6Result> recognize(
            @Parameter(description = "待识别的图片文件（支持 PNG/JPG/BMP 等标准格式）", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.run(file.getBytes());
    }

    /**
     * 身份证结构化识别（支持正反面自动识别）
     */
    @Operation(summary = "身份证结构化解析", description = "自动判断身份证正面（人像面）或反面（国徽面），并抽取姓名、性别、民族、出生日期、住址、身份证号、签发机关、有效期限等业务字段")
    @PostMapping(value = "/id-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IdCardResult recognizeIdCard(
            @Parameter(description = "身份证照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.idCard().parse(file.getBytes());
    }

    /**
     * 机动车行驶证结构化识别
     */
    @Operation(summary = "行驶证结构化解析", description = "精准解析行驶证号牌号码、车辆类型、所有人、住址、使用性质、品牌型号、车辆识别代号(VIN)、发动机号码、注册日期、发证日期等关键属性")
    @PostMapping(value = "/vehicle", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VehicleLicenseResult recognizeVehicle(
            @Parameter(description = "行驶证照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.vehicleLicense().parse(file.getBytes());
    }

    /**
     * 机动车驾驶证结构化识别
     */
    @Operation(summary = "驾驶证结构化解析", description = "解析驾驶证证号、姓名、性别、国籍、住址、出生日期、初次领证日期、准驾车型、有效期限等")
    @PostMapping(value = "/driver-license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DriverLicenseResult recognizeDriverLicense(
            @Parameter(description = "驾驶证照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.driverLicense().parse(file.getBytes());
    }

    /**
     * 营业执照结构化识别
     */
    @Operation(summary = "营业执照结构化解析", description = "解析统一社会信用代码、名称、类型、法定代表人、注册资本、成立日期、营业期限、住所、经营范围等")
    @PostMapping(value = "/business-license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BusinessLicenseResult recognizeBusinessLicense(
            @Parameter(description = "营业执照照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.businessLicense().parse(file.getBytes());
    }

    /**
     * 银行卡结构化识别
     */
    @Operation(summary = "银行卡结构化解析", description = "识别银行卡卡号、发卡行名称、卡片类型（借记卡/信用卡）等")
    @PostMapping(value = "/bank-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankCardResult recognizeBankCard(
            @Parameter(description = "银行卡照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.bankCard().parse(file.getBytes());
    }

    /**
     * 增值税发票结构化识别
     */
    @Operation(summary = "增值税发票结构化解析", description = "解析发票代码、发票号码、开票日期、购买方名称及税号、销售方名称及税号、合计金额、合计税额、价税合计等")
    @PostMapping(value = "/invoice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InvoiceResult recognizeInvoice(
            @Parameter(description = "发票照片或 PDF 截屏图片", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.invoice().parse(file.getBytes());
    }

    /**
     * 火车票结构化识别
     */
    @Operation(summary = "火车票结构化解析", description = "解析始发站、到达站、车次、出发日期、时间、座位号、席别、乘客姓名、身份证号、车票金额、车票号等")
    @PostMapping(value = "/train-ticket", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TrainTicketResult recognizeTrainTicket(
            @Parameter(description = "火车票照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.trainTicket().parse(file.getBytes());
    }

    /**
     * 出租车票结构化识别
     */
    @Operation(summary = "出租车票结构化解析", description = "解析发票代码、发票号码、车牌号、日期、上下车时间、里程、金额、燃油附加费、总金额、开票城市等")
    @PostMapping(value = "/taxi-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TaxiReceiptResult recognizeTaxiReceipt(
            @Parameter(description = "出租车票照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.taxiReceipt().parse(file.getBytes());
    }

    /**
     * 户口本结构化识别
     */
    @Operation(summary = "户口本结构化解析", description = "解析户号、姓名、与户主关系、性别、出生地、民族、籍贯、出生日期、公民身份号码、身高、文化程度、服务处所等")
    @PostMapping(value = "/household-register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HouseholdRegisterResult recognizeHouseholdRegister(
            @Parameter(description = "户口本（常住人口登记卡）照片文件", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        return ppocr.householdRegister().parse(file.getBytes());
    }

}

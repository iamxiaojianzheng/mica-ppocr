package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 机动车行驶证解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleLicenseResultDto {

    /**
     * 号牌号码: 如 "鲁GH9P12"
     */
    private String plateNo;

    /**
     * 车辆类型: 如 "小型普通客车"
     */
    private String vehicleType;

    /**
     * 所有人 (车主姓名或公司单位名称)
     */
    private String owner;

    /**
     * 住址
     */
    private String address;

    /**
     * 使用性质: 如 "非营运"
     */
    private String useCharacter;

    /**
     * 品牌型号
     */
    private String model;

    /**
     * 车辆识别代号 (VIN 码)
     */
    private String vin;

    /**
     * 发动机号码
     */
    private String engineNo;

    /**
     * 注册日期: 格式 "yyyy-MM-dd"
     */
    private String registerDate;

    /**
     * 发证日期: 格式 "yyyy-MM-dd"
     */
    private String issueDate;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getPlateNo() {
        return plateNo;
    }

    public void setPlateNo(String plateNo) {
        this.plateNo = plateNo;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUseCharacter() {
        return useCharacter;
    }

    public void setUseCharacter(String useCharacter) {
        this.useCharacter = useCharacter;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getEngineNo() {
        return engineNo;
    }

    public void setEngineNo(String engineNo) {
        this.engineNo = engineNo;
    }

    public String getRegisterDate() {
        return registerDate;
    }

    public void setRegisterDate(String registerDate) {
        this.registerDate = registerDate;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

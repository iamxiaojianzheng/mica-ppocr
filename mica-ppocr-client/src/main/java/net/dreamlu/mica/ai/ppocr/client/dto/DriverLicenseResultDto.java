package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 机动车驾驶证解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverLicenseResultDto {

    /**
     * 驾驶证证号 (一般同身份证号)
     */
    private String licenseNumber;

    /**
     * 驾驶人姓名
     */
    private String name;

    /**
     * 性别: 男 / 女
     */
    private String gender;

    /**
     * 国籍: 如 "中国"
     */
    private String nationality;

    /**
     * 住址
     */
    private String address;

    /**
     * 出生日期: 格式 "yyyy-MM-dd"
     */
    private String birthDate;

    /**
     * 初次领证日期: 格式 "yyyy-MM-dd"
     */
    private String issueDate;

    /**
     * 准驾车型: 如 "C1", "A2", "B1" 等
     */
    private String vehicleClass;

    /**
     * 签发机关: 如 "北京市公安局公安交通管理局"
     */
    private String issuingAuthority;

    /**
     * 有效期限起始日期: 格式 "yyyy-MM-dd"
     */
    private String validFrom;

    /**
     * 有效期限截止日期: 格式 "yyyy-MM-dd" 或 "长期"
     */
    private String validTo;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(String issueDate) {
        this.issueDate = issueDate;
    }

    public String getVehicleClass() {
        return vehicleClass;
    }

    public void setVehicleClass(String vehicleClass) {
        this.vehicleClass = vehicleClass;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public void setIssuingAuthority(String issuingAuthority) {
        this.issuingAuthority = issuingAuthority;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

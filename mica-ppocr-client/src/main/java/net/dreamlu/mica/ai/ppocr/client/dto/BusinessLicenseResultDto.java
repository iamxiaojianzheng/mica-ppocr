package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 营业执照解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessLicenseResultDto {

    /**
     * 统一社会信用代码 (18 位字母与数字组合)
     */
    private String creditCode;

    /**
     * 单位名称 / 企业名称
     */
    private String name;

    /**
     * 公司类型: 如 "有限责任公司(自然人投资或控股)"
     */
    private String type;

    /**
     * 法定代表人 / 负责人
     */
    private String legalPerson;

    /**
     * 注册资本: 如 "壹千万元整"
     */
    private String registeredCapital;

    /**
     * 成立日期: 如 "2020年01月01日"
     */
    private String establishDate;

    /**
     * 营业期限 / 有效日期至: 如 "长期" 或 "2020年01月01日至2050年12月31日"
     */
    private String operatingPeriod;

    /**
     * 住所 / 经营场所地址
     */
    private String address;

    /**
     * 许可经营范围
     */
    private String businessScope;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLegalPerson() {
        return legalPerson;
    }

    public void setLegalPerson(String legalPerson) {
        this.legalPerson = legalPerson;
    }

    public String getRegisteredCapital() {
        return registeredCapital;
    }

    public void setRegisteredCapital(String registeredCapital) {
        this.registeredCapital = registeredCapital;
    }

    public String getEstablishDate() {
        return establishDate;
    }

    public void setEstablishDate(String establishDate) {
        this.establishDate = establishDate;
    }

    public String getOperatingPeriod() {
        return operatingPeriod;
    }

    public void setOperatingPeriod(String operatingPeriod) {
        this.operatingPeriod = operatingPeriod;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getBusinessScope() {
        return businessScope;
    }

    public void setBusinessScope(String businessScope) {
        this.businessScope = businessScope;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

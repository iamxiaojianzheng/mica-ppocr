package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 身份证解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdCardResultDto {

    /**
     * 身份证面别: FRONT (人像面 / 正面), BACK (国徽面 / 反面)
     */
    private String side;

    /**
     * 姓名 (仅正面)
     */
    private String name;

    /**
     * 性别: 男 / 女 (仅正面)
     */
    private String gender;

    /**
     * 民族: 如 "汉", "满", "壮" 等 (仅正面)
     */
    private String ethnicity;

    /**
     * 出生日期: 格式 "yyyy-MM-dd" (仅正面)
     */
    private String birthDate;

    /**
     * 住址 (仅正面)
     */
    private String address;

    /**
     * 18 位公民身份号码 (仅正面)
     */
    private String idNumber;

    /**
     * 签发机关: 如 "北京市公安局海淀分局" (仅反面)
     */
    private String authority;

    /**
     * 有效期限: 如 "2015.01.01-2035.01.01" 或 "长期" (仅反面)
     */
    private String validPeriod;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
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

    public String getEthnicity() {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity) {
        this.ethnicity = ethnicity;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getValidPeriod() {
        return validPeriod;
    }

    public void setValidPeriod(String validPeriod) {
        this.validPeriod = validPeriod;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

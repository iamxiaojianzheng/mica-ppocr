package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 户口本（常住人口登记卡）解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdRegisterResultDto {

    /**
     * 户号 (7 ~ 12 位数字)
     */
    private String householdNo;

    /**
     * 姓名
     */
    private String name;

    /**
     * 与户主关系: 如 "户主", "妻", "子", "女" 等
     */
    private String relationship;

    /**
     * 性别: 男 / 女
     */
    private String gender;

    /**
     * 出生地: 如 "四川省"
     */
    private String birthPlace;

    /**
     * 民族: 如 "汉族"
     */
    private String ethnicity;

    /**
     * 籍贯: 如 "四川省成都"
     */
    private String nativePlace;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 18 位公民身份号码
     */
    private String idNumber;

    /**
     * 身高: 如 "175cm"
     */
    private String height;

    /**
     * 文化程度: 如 "大学本科", "高中"
     */
    private String education;

    /**
     * 服务处所 (工作单位)
     */
    private String workplace;

    /**
     * 何时由何地迁来本市(县)
     */
    private String moveToCityDate;

    /**
     * 何时由何地迁往本址
     */
    private String moveToAddress;

    /**
     * 登记日期: 如 "2015年05月10日"
     */
    private String registrationDate;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getHouseholdNo() {
        return householdNo;
    }

    public void setHouseholdNo(String householdNo) {
        this.householdNo = householdNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity) {
        this.ethnicity = ethnicity;
    }

    public String getNativePlace() {
        return nativePlace;
    }

    public void setNativePlace(String nativePlace) {
        this.nativePlace = nativePlace;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getHeight() {
        return height;
    }

    public void setHeight(String height) {
        this.height = height;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public String getWorkplace() {
        return workplace;
    }

    public void setWorkplace(String workplace) {
        this.workplace = workplace;
    }

    public String getMoveToCityDate() {
        return moveToCityDate;
    }

    public void setMoveToCityDate(String moveToCityDate) {
        this.moveToCityDate = moveToCityDate;
    }

    public String getMoveToAddress() {
        return moveToAddress;
    }

    public void setMoveToAddress(String moveToAddress) {
        this.moveToAddress = moveToAddress;
    }

    public String getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

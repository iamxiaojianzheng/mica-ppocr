package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 银行卡解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankCardResultDto {

    /**
     * 银行卡卡号 (16 ~ 19 位数字)
     */
    private String cardNumber;

    /**
     * 有效期: 格式 "MM/YY"
     */
    private String validDate;

    /**
     * 持卡人姓名
     */
    private String holderName;

    /**
     * 发卡银行名称: 如 "中国工商银行", "招商银行"
     */
    private String bankName;

    /**
     * 卡片类型: DEBIT (借记卡), CREDIT (信用卡)
     */
    private String cardType;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getValidDate() {
        return validDate;
    }

    public void setValidDate(String validDate) {
        this.validDate = validDate;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

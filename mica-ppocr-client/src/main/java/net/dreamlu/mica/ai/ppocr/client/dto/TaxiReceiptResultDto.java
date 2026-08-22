package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 出租车票解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaxiReceiptResultDto {

    /**
     * 发票代码 (12 位数字)
     */
    private String invoiceCode;

    /**
     * 发票号码 (8 位数字)
     */
    private String invoiceNo;

    /**
     * 车牌号: 如 "京A12345"
     */
    private String plateNumber;

    /**
     * 日期: 格式 "yyyy-MM-dd"
     */
    private String date;

    /**
     * 上车时间: 格式 "HH:mm"
     */
    private String boardingTime;

    /**
     * 下车时间: 格式 "HH:mm"
     */
    private String alightingTime;

    /**
     * 行驶里程 (公里)
     */
    private String mileage;

    /**
     * 计费金额 (不含附加费)
     */
    private String amount;

    /**
     * 燃油附加费
     */
    private String fuelSurcharge;

    /**
     * 叫车服务费
     */
    private String bookingFee;

    /**
     * 总金额 (包含附加费)
     */
    private String totalAmount;

    /**
     * 开票城市
     */
    private String city;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getInvoiceCode() {
        return invoiceCode;
    }

    public void setInvoiceCode(String invoiceCode) {
        this.invoiceCode = invoiceCode;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getBoardingTime() {
        return boardingTime;
    }

    public void setBoardingTime(String boardingTime) {
        this.boardingTime = boardingTime;
    }

    public String getAlightingTime() {
        return alightingTime;
    }

    public void setAlightingTime(String alightingTime) {
        this.alightingTime = alightingTime;
    }

    public String getMileage() {
        return mileage;
    }

    public void setMileage(String mileage) {
        this.mileage = mileage;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getFuelSurcharge() {
        return fuelSurcharge;
    }

    public void setFuelSurcharge(String fuelSurcharge) {
        this.fuelSurcharge = fuelSurcharge;
    }

    public String getBookingFee() {
        return bookingFee;
    }

    public void setBookingFee(String bookingFee) {
        this.bookingFee = bookingFee;
    }

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

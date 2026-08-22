package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 火车票解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainTicketResultDto {

    /**
     * 始发站: 如 "北京南"
     */
    private String departure;

    /**
     * 到达站: 如 "上海虹桥"
     */
    private String arrival;

    /**
     * 车次: 如 "G123"
     */
    private String trainNumber;

    /**
     * 出发日期: 格式 "yyyy年MM月dd日"
     */
    private String departureDate;

    /**
     * 出发时间: 格式 "HH:mm"
     */
    private String departureTime;

    /**
     * 座位号: 如 "05车12A号"
     */
    private String seatNumber;

    /**
     * 席别: 如 "二等座", "一等座", "商务座", "硬卧"
     */
    private String seatClass;

    /**
     * 乘客姓名
     */
    private String passengerName;

    /**
     * 乘客身份证号 (含星号掩码)
     */
    private String idNumber;

    /**
     * 车票金额: 如 "￥26.00元"
     */
    private String amount;

    /**
     * 不含税金额
     */
    private String amountExcludingTax;

    /**
     * 车票号 (10 位数字)
     */
    private String ticketNo;

    /**
     * 发票号码 (电子客票 20 位)
     */
    private String invoiceNo;

    /**
     * 电子客票号 (25 位数字)
     */
    private String eTicketNo;

    /**
     * 开票日期: 格式 "yyyy年MM月dd日"
     */
    private String invoiceDate;

    /**
     * 售站名称
     */
    private String sellStation;

    /**
     * 序列号
     */
    private String serialNumber;

    /**
     * 改签标识: 如 "始发改签", "退票"
     */
    private String changedFlag;

    /**
     * 原始 OCR 散落文本检测结果列表
     */
    private List<OcrResultDto> rawResults;

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public String getSeatClass() {
        return seatClass;
    }

    public void setSeatClass(String seatClass) {
        this.seatClass = seatClass;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getAmountExcludingTax() {
        return amountExcludingTax;
    }

    public void setAmountExcludingTax(String amountExcludingTax) {
        this.amountExcludingTax = amountExcludingTax;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getETicketNo() {
        return eTicketNo;
    }

    public void setETicketNo(String eTicketNo) {
        this.eTicketNo = eTicketNo;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getSellStation() {
        return sellStation;
    }

    public void setSellStation(String sellStation) {
        this.sellStation = sellStation;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getChangedFlag() {
        return changedFlag;
    }

    public void setChangedFlag(String changedFlag) {
        this.changedFlag = changedFlag;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

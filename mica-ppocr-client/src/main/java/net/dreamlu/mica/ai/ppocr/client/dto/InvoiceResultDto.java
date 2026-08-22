package net.dreamlu.mica.ai.ppocr.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 增值税发票解析结果 DTO (JDK 8 兼容)
 *
 * @author Antigravity
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceResultDto {

    /**
     * 发票代码
     */
    private String invoiceCode;

    /**
     * 发票号码
     */
    private String invoiceNo;

    /**
     * 开票日期: 如 "2023年06月15日"
     */
    private String invoiceDate;

    /**
     * 购买方名称
     */
    private String buyerName;

    /**
     * 购买方纳税人识别号 / 统一社会信用代码
     */
    private String buyerTaxNo;

    /**
     * 购买方地址、电话
     */
    private String buyerAddressPhone;

    /**
     * 购买方开户行及账号
     */
    private String buyerBankAccount;

    /**
     * 销售方名称
     */
    private String sellerName;

    /**
     * 销售方纳税人识别号 / 统一社会信用代码
     */
    private String sellerTaxNo;

    /**
     * 销售方地址、电话
     */
    private String sellerAddressPhone;

    /**
     * 销售方开户行及账号
     */
    private String sellerBankAccount;

    /**
     * 货物或应税劳务、服务名称
     */
    private String goodsName;

    /**
     * 不含税金额
     */
    private String amount;

    /**
     * 税率
     */
    private String taxRate;

    /**
     * 税额
     */
    private String taxAmount;

    /**
     * 价税合计 (大写): 如 "壹佰贰拾元整"
     */
    private String totalAmountUpper;

    /**
     * 价税合计 (小写): 如 "120.00"
     */
    private String totalAmountLower;

    /**
     * 收款人
     */
    private String payee;

    /**
     * 复核人
     */
    private String reviewer;

    /**
     * 开票人
     */
    private String issuer;

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

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(String invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerTaxNo() {
        return buyerTaxNo;
    }

    public void setBuyerTaxNo(String buyerTaxNo) {
        this.buyerTaxNo = buyerTaxNo;
    }

    public String getBuyerAddressPhone() {
        return buyerAddressPhone;
    }

    public void setBuyerAddressPhone(String buyerAddressPhone) {
        this.buyerAddressPhone = buyerAddressPhone;
    }

    public String getBuyerBankAccount() {
        return buyerBankAccount;
    }

    public void setBuyerBankAccount(String buyerBankAccount) {
        this.buyerBankAccount = buyerBankAccount;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getSellerTaxNo() {
        return sellerTaxNo;
    }

    public void setSellerTaxNo(String sellerTaxNo) {
        this.sellerTaxNo = sellerTaxNo;
    }

    public String getSellerAddressPhone() {
        return sellerAddressPhone;
    }

    public void setSellerAddressPhone(String sellerAddressPhone) {
        this.sellerAddressPhone = sellerAddressPhone;
    }

    public String getSellerBankAccount() {
        return sellerBankAccount;
    }

    public void setSellerBankAccount(String sellerBankAccount) {
        this.sellerBankAccount = sellerBankAccount;
    }

    public String getGoodsName() {
        return goodsName;
    }

    public void setGoodsName(String goodsName) {
        this.goodsName = goodsName;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(String taxRate) {
        this.taxRate = taxRate;
    }

    public String getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(String taxAmount) {
        this.taxAmount = taxAmount;
    }

    public String getTotalAmountUpper() {
        return totalAmountUpper;
    }

    public void setTotalAmountUpper(String totalAmountUpper) {
        this.totalAmountUpper = totalAmountUpper;
    }

    public String getTotalAmountLower() {
        return totalAmountLower;
    }

    public void setTotalAmountLower(String totalAmountLower) {
        this.totalAmountLower = totalAmountLower;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public List<OcrResultDto> getRawResults() {
        return rawResults;
    }

    public void setRawResults(List<OcrResultDto> rawResults) {
        this.rawResults = rawResults;
    }
}

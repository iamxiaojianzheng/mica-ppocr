package net.dreamlu.mica.ai.ppocr.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dreamlu.mica.ai.ppocr.client.dto.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * mica-ppocr SDK 远程客户端 (JDK 8 兼容全量版)
 * <p>
 * 覆盖全部 9 种结构化卡证票据与通用 OCR：
 * <ul>
 *   <li>{@link #idCard()} 身份证</li>
 *   <li>{@link #vehicleLicense()} 行驶证</li>
 *   <li>{@link #driverLicense()} 驾驶证</li>
 *   <li>{@link #businessLicense()} 营业执照</li>
 *   <li>{@link #bankCard()} 银行卡</li>
 *   <li>{@link #invoice()} 增值税发票</li>
 *   <li>{@link #trainTicket()} 火车票</li>
 *   <li>{@link #taxiReceipt()} 出租车票</li>
 *   <li>{@link #householdRegister()} 户口本（常住人口登记卡）</li>
 * </ul>
 *
 * @author Antigravity
 */
public class PPOcrClient {

    private final String serverUrl;
    private final ObjectMapper objectMapper;

    public PPOcrClient(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("serverUrl 不能为空");
        }
        this.serverUrl = serverUrl.replaceAll("/+$", "");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 检查远程服务健康状态
     */
    public boolean isHealthy() {
        try {
            URL url = new URL(serverUrl + "/api/ocr/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用 OCR 识别（返回散落文本框列表）
     */
    public List<OcrResultDto> recognize(byte[] imageBytes) {
        return postMultipart("/api/ocr/recognize", imageBytes, "file.jpg", new TypeReference<List<OcrResultDto>>() {});
    }

    public List<OcrResultDto> recognize(File file) {
        return recognize(readFileToBytes(file));
    }

    /**
     * 1. 身份证解析器 Client
     */
    public ParserClient<IdCardResultDto> idCard() {
        return new ParserClient<>(this, "/api/ocr/id-card", IdCardResultDto.class);
    }

    /**
     * 2. 机动车行驶证解析器 Client
     */
    public ParserClient<VehicleLicenseResultDto> vehicleLicense() {
        return new ParserClient<>(this, "/api/ocr/vehicle", VehicleLicenseResultDto.class);
    }

    /**
     * 3. 机动车驾驶证解析器 Client
     */
    public ParserClient<DriverLicenseResultDto> driverLicense() {
        return new ParserClient<>(this, "/api/ocr/driver-license", DriverLicenseResultDto.class);
    }

    /**
     * 4. 营业执照解析器 Client
     */
    public ParserClient<BusinessLicenseResultDto> businessLicense() {
        return new ParserClient<>(this, "/api/ocr/business-license", BusinessLicenseResultDto.class);
    }

    /**
     * 5. 银行卡解析器 Client
     */
    public ParserClient<BankCardResultDto> bankCard() {
        return new ParserClient<>(this, "/api/ocr/bank-card", BankCardResultDto.class);
    }

    /**
     * 6. 增值税发票解析器 Client
     */
    public ParserClient<InvoiceResultDto> invoice() {
        return new ParserClient<>(this, "/api/ocr/invoice", InvoiceResultDto.class);
    }

    /**
     * 7. 火车票解析器 Client
     */
    public ParserClient<TrainTicketResultDto> trainTicket() {
        return new ParserClient<>(this, "/api/ocr/train-ticket", TrainTicketResultDto.class);
    }

    /**
     * 8. 出租车票解析器 Client
     */
    public ParserClient<TaxiReceiptResultDto> taxiReceipt() {
        return new ParserClient<>(this, "/api/ocr/taxi-receipt", TaxiReceiptResultDto.class);
    }

    /**
     * 9. 户口本（常住人口登记卡）解析器 Client
     */
    public ParserClient<HouseholdRegisterResultDto> householdRegister() {
        return new ParserClient<>(this, "/api/ocr/household-register", HouseholdRegisterResultDto.class);
    }

    /**
     * 结构化解析子客户端句柄
     */
    public static class ParserClient<T> {
        private final PPOcrClient client;
        private final String endpoint;
        private final Class<T> clazz;

        public ParserClient(PPOcrClient client, String endpoint, Class<T> clazz) {
            this.client = client;
            this.endpoint = endpoint;
            this.clazz = clazz;
        }

        public T parse(byte[] imageBytes) {
            return client.postMultipart(endpoint, imageBytes, "image.jpg", clazz);
        }

        public T parse(File file) {
            return parse(client.readFileToBytes(file));
        }
    }

    /**
     * 核心 HTTP Multipart 请求执行器 (使用纯 Java 8 原生 API 构建，零第三方库强制依赖)
     */
    public <T> T postMultipart(String path, byte[] fileBytes, String fileName, Class<T> clazz) {
        String json = executeHttpMultipart(path, fileBytes, fileName);
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("反序列化 OCR 服务响应数据失败: " + json, e);
        }
    }

    public <T> T postMultipart(String path, byte[] fileBytes, String fileName, TypeReference<T> typeRef) {
        String json = executeHttpMultipart(path, fileBytes, fileName);
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("反序列化 OCR 服务响应数据失败: " + json, e);
        }
    }

    private String executeHttpMultipart(String path, byte[] fileBytes, String fileName) {
        String boundary = "===PPOcrClientBoundary" + System.currentTimeMillis() + "===";
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("User-Agent", "mica-ppocr-client/1.1.6");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = new DataOutputStream(conn.getOutputStream())) {
                StringBuilder sb = new StringBuilder();
                sb.append(twoHyphens).append(boundary).append(lineEnd);
                sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"").append(lineEnd);
                sb.append("Content-Type: application/octet-stream").append(lineEnd);
                sb.append(lineEnd);

                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                out.write(fileBytes);
                out.write(lineEnd.getBytes(StandardCharsets.UTF_8));

                String endBoundary = twoHyphens + boundary + twoHyphens + lineEnd;
                out.write(endBoundary.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            InputStream is = (responseCode == HttpURLConnection.HTTP_OK) ? conn.getInputStream() : conn.getErrorStream();
            String responseStr = readStreamToString(is);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("调用远程 mica-ppocr 服务异常 (HTTP " + responseCode + "): " + responseStr);
            }
            return responseStr;

        } catch (Exception e) {
            throw new RuntimeException("连接远程 mica-ppocr 服务失败 [" + serverUrl + path + "]: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private byte[] readFileToBytes(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("读取本地图片文件失败: " + file.getAbsolutePath(), e);
        }
    }

    private String readStreamToString(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }
}

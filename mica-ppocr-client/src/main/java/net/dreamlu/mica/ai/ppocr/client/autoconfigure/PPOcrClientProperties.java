package net.dreamlu.mica.ai.ppocr.client.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * mica-ppocr-client 配置属性
 *
 * @author Antigravity
 */
@ConfigurationProperties(prefix = "mica.ai.ppocr.client")
public class PPOcrClientProperties {

    /**
     * 远程 Docker / 微服务地址 (例如: http://192.168.1.100:8090)
     */
    private String url = "http://localhost:8090";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

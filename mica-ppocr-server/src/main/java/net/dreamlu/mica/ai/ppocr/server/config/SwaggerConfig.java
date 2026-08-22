package net.dreamlu.mica.ai.ppocr.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 配置类
 *
 * @author Antigravity
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("mica-ppocr REST API 文字识别与结构化服务")
                        .description("基于 PP-OCRv6 纯 ONNXRuntime 推理（Java 17 移植版）的开箱即用 OCR 微服务，提供通用文本检测识别及各类证件/票据的高精结构化解析功能。")
                        .version("1.1.6")
                        .contact(new Contact()
                                .name("ChunmengLu")
                                .email("qq596392912@gmail.com")
                                .url("https://www.dreamlu.net"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

}

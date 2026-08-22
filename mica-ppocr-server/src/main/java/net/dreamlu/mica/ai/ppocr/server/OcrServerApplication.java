package net.dreamlu.mica.ai.ppocr.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mica-ppocr REST Web 服务引导启动类
 *
 * @author Antigravity
 */
@SpringBootApplication
public class OcrServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrServerApplication.class, args);
    }

}

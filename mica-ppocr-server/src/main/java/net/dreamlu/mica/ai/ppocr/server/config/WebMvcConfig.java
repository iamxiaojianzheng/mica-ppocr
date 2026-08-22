package net.dreamlu.mica.ai.ppocr.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：根路径访问时跳转至 Knife4j API 文档测试主页
 *
 * @author Antigravity
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 访问 根路径 / 时直接跳转到 Knife4j 文档 UI (doc.html)
        registry.addRedirectViewController("/", "/doc.html");
    }

}

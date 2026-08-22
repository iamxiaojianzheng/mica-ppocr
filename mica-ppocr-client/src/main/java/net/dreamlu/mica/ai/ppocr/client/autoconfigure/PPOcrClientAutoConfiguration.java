package net.dreamlu.mica.ai.ppocr.client.autoconfigure;

import net.dreamlu.mica.ai.ppocr.client.PPOcrClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PPOcrClient Spring Boot 自动配置类
 *
 * @author Antigravity
 */
@Configuration
@EnableConfigurationProperties(PPOcrClientProperties.class)
public class PPOcrClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PPOcrClient ppOcrClient(PPOcrClientProperties properties) {
        return new PPOcrClient(properties.getUrl());
    }

}

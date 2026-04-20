package com.example.zhihuiqingtu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类。
 * <p>
 * 当前项目中 Java 主后端需要通过 HTTP 调用 Python 推荐服务，
 * 所以这里统一向 Spring 容器中注册一个 RestTemplate 对象。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建一个 RestTemplate Bean。
     *
     * @return 可用于发送 HTTP 请求的 RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

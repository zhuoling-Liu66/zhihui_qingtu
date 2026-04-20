package com.example.zhihuiqingtu.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地上传资源映射配置。
 * <p>
 * 活动封面会被保存到本地磁盘，而浏览器只能通过 URL 访问资源。
 * 因此这里把本地 uploads 目录映射为 `/uploads/**`，
 * 这样前端就可以直接通过封面 URL 显示图片。
 */
@Configuration
@RequiredArgsConstructor
public class StaticResourceConfig implements WebMvcConfigurer {

    /**
     * 上传配置对象，用于读取本地上传目录。
     */
    private final UploadProperties uploadProperties;

    /**
     * 注册静态资源访问规则。
     *
     * @param registry Spring MVC 的资源处理注册器
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        try {
            // 启动时确保上传目录存在，避免第一次访问或上传时报错。
            Files.createDirectories(uploadRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建上传资源目录: " + uploadRoot, exception);
        }

        String location = uploadRoot.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}

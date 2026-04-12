package com.example.zhihuiqingtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.nio.file.Paths;

/**
 * 上传配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.upload")
public class UploadProperties {

    /**
     * 上传根目录。
     */
    private String baseDir = Paths.get(System.getProperty("user.dir"), "uploads").toString();

    /**
     * 活动封面最大大小，默认 5MB。
     */
    private DataSize maxCoverSize = DataSize.ofMegabytes(5);

    public String getActivityCoverDir() {
        return Paths.get(StringUtils.hasText(baseDir) ? baseDir : ".", "activity-covers").toString();
    }
}

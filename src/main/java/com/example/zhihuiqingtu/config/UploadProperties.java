package com.example.zhihuiqingtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.nio.file.Paths;

/**
 * 上传配置类。
 * <p>
 * 用于读取并保存与文件上传有关的配置项，
 * 例如上传目录、封面大小限制等。
 */
@Data
@Component
@ConfigurationProperties(prefix = "campus.upload")
public class UploadProperties {

    /**
     * 上传根目录。
     * 默认值为项目运行目录下的 uploads 文件夹。
     */
    private String baseDir = Paths.get(System.getProperty("user.dir"), "uploads").toString();

    /**
     * 活动封面允许上传的最大大小，默认 5MB。
     */
    private DataSize maxCoverSize = DataSize.ofMegabytes(5);

    /**
     * 获取“活动封面”专属子目录。
     *
     * @return activity-covers 目录路径
     */
    public String getActivityCoverDir() {
        return Paths.get(StringUtils.hasText(baseDir) ? baseDir : ".", "activity-covers").toString();
    }
}

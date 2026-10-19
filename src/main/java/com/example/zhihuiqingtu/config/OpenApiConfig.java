package com.example.zhihuiqingtu.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置。
 * <p>
 * 访问地址：{@code http://localhost:8080/swagger-ui.html}
 * <p>
 * 这里只声明文档的"封面信息"（标题、版本、联系人）。
 * 接口本身不需要在这里登记 —— springdoc 在启动时扫描所有
 * {@code @RestController}，把方法签名和参数类型自动转成 OpenAPI 描述，
 * 所以新增接口不用改这个类。
 * <p>
 * <b>为什么是 springdoc 而不是 knife4j：</b>
 * knife4j 是套在 springdoc 外面的中文增强 UI，但它最新版 4.5.0 停留在
 * 2024-01，依赖 springdoc 2.x（Spring Boot 3 / Framework 6 时代）。
 * 本项目是 Spring Boot 4（Framework 7），实测两种接法都会抛
 * {@code NoSuchMethodError}：
 * <ul>
 *   <li>用 knife4j 自带的 springdoc 2.3.0 → {@code ControllerAdviceBean.<init>(Object)}
 *       这个构造函数在 Framework 7 里已被移除；</li>
 *   <li>把 springdoc 强制换成 3.x → knife4j 自己的自动配置代码在调
 *       {@code SpringDocConfigProperties.getGroupConfigs()}，该 API 在 3.x 里已改签名。</li>
 * </ul>
 * 所以这里直接用 springdoc 3.x（官方对应 Boot 4 的版本线）。
 * 代价是没有 knife4j 的离线文档导出、接口排序等增强功能，
 * 换来的是文档页真的能打开。
 */
@Configuration
public class OpenApiConfig {

    /**
     * OpenAPI 文档的元信息。
     *
     * @return 文档描述对象
     */
    @Bean
    public OpenAPI zhihuiqingtuOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("智慧青途 接口文档")
                .description("校园活动发布、报名、学分审核与团队管理平台")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("zhihuiqingtu")));
    }
}

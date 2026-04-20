package com.example.zhihuiqingtu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类。
 * <p>
 * 这是整个 Spring Boot 后端的入口文件。
 * 当我们运行 main 方法时，Spring Boot 会：
 * <ol>
 *     <li>创建并启动 Spring 容器。</li>
 *     <li>扫描项目中的 Controller、Service、Repository 等组件。</li>
 *     <li>读取 application.properties 中的配置。</li>
 *     <li>启动内嵌的 Web 服务器，让接口可以通过 HTTP 被访问。</li>
 * </ol>
 */
@SpringBootApplication
// 扫描 repository 包下的 Mapper 接口，让 MyBatis-Plus 为它们创建代理对象。
@MapperScan("com.example.zhihuiqingtu.repository")
public class ZhihuiqingtuApplication {

    /**
     * Java 程序的启动入口。
     *
     * @param args 启动参数，一般开发阶段不会手动传递
     */
    public static void main(String[] args) {
        SpringApplication.run(ZhihuiqingtuApplication.class, args);
    }
}

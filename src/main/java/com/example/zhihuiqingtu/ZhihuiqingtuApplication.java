package com.example.zhihuiqingtu;

import com.example.zhihuiqingtu.common.JwtProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 项目启动入口。
 *
 * <p>除标准的组件扫描与 Mapper 扫描外，这里显式注册 {@link JwtProperties}。
 * 用 {@code @EnableConfigurationProperties} 而不是给配置类加 {@code @Component}，
 * 是因为 record 无法被组件扫描实例化，构造器绑定需要由配置属性基础设施注册。
 */
@SpringBootApplication
// 扫描 repository 包下的 Mapper 接口，让 MyBatis-Plus 为它们创建代理对象。
@MapperScan("com.example.zhihuiqingtu.repository")
@EnableConfigurationProperties(JwtProperties.class)
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

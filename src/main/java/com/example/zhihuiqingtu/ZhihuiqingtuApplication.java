package com.example.zhihuiqingtu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.zhihuiqingtu.repository")
public class ZhihuiqingtuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhihuiqingtuApplication.class, args);
    }

}

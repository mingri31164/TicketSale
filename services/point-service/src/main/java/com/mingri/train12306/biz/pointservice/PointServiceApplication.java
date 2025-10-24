package com.mingri.train12306.biz.pointservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 积分服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.mingri.train12306.biz.pointservice.dao.mapper")
public class PointServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PointServiceApplication.class, args);
    }
}


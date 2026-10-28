package com.zhe.mall.pay;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类。
 *
 * <p>{@code @MapperScan} 同时声明本服务与 mall-common 的消息表 Mapper 包；
 * 理由见 {@code MallOrderApplication} 的注释（自动扫描扫不到 mall-common，
 * 而自注册扫描器又会压制自动扫描）。
 */
@EnableScheduling
@EnableFeignClients(basePackages = "com.zhe.mall.api")
@MapperScan({"com.zhe.mall.pay.mapper", "com.zhe.mall.common.mq"})
@SpringBootApplication
public class MallPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallPayApplication.class, args);
    }
}

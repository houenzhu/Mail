package com.zhe.mall.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务启动类。
 *
 * <p>{@code @MapperScan} 同时声明本服务与 mall-common 的消息表 Mapper 包
 * （本服务消费 {@code TOPIC_ORDER_PAID}，需要 {@code ConsumedMessageMapper}）。
 * 必须显式列出，原因见 {@code MallOrderApplication} 的注释：一旦在别处注册
 * Mapper 扫描器，MyBatis 的自动扫描会整体退避，业务 Mapper 会一起消失。
 */
@MapperScan({"com.zhe.mall.product.mapper", "com.zhe.mall.common.mq"})
@EnableFeignClients(basePackages = "com.zhe.mall.api")
@SpringBootApplication
public class MallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallProductApplication.class, args);
    }
}

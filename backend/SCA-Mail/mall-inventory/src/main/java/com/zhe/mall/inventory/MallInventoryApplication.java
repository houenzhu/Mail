package com.zhe.mall.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 库存服务启动类。
 *
 * <p>{@code @MapperScan} 同时声明本服务与 mall-common 的消息表 Mapper 包
 * （本服务只消费，用到 {@code ConsumedMessageMapper}）；
 * 理由见 {@code MallOrderApplication} 的注释。
 */
@MapperScan({"com.zhe.mall.inventory.mapper", "com.zhe.mall.common.mq"})
@SpringBootApplication
public class MallInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallInventoryApplication.class, args);
    }
}

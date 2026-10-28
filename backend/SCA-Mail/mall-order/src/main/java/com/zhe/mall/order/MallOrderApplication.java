package com.zhe.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 *
 * <p>{@code @MapperScan} 显式声明两个包：本服务的 Mapper，以及 mall-common 的消息表 Mapper
 * （{@code LocalMessageMapper} / {@code ConsumedMessageMapper}）。
 * 必须显式声明的原因：MyBatis 的自动扫描（{@code AutoConfiguredMapperScannerRegistrar}）
 * 只会扫启动类所在的包，扫不到 mall-common 里的 Mapper；而一旦在别处注册
 * {@code MapperScannerConfigurer} / {@code MapperFactoryBean}，自动扫描又会整体退避 ——
 * 结果是业务 Mapper 一起消失（实测踩坑）。显式列出两个包可以彻底绕开这个互相压制。
 */
@EnableScheduling
@EnableFeignClients(basePackages = "com.zhe.mall.api")
@MapperScan({"com.zhe.mall.order.mapper", "com.zhe.mall.common.mq"})
@SpringBootApplication
public class MallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderApplication.class, args);
    }
}

package com.zhe.mall.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 后台 BFF（预留端口 8111）。
 *
 * <p>职责：把后台管理端需要的数据从各领域服务聚合起来，对外统一暴露 {@code /api/admin/**}
 * （见需求文档 655 行约定与计划文档 ADR-N1）。它**不持有自己的业务库**，
 * 也不做业务规则判断——规则留在各领域服务内，避免同一规则出现两份实现。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhe.mall.api")
public class MallAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAdminApplication.class, args);
    }
}

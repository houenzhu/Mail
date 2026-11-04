package com.zhe.mall.inventory.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存 Mapper XML 的离线校验。
 *
 * <p>只用 XMLMapperBuilder 解析真实文件：本项目 Mapper 少、SQL 简单，
 * 但这层能挡住"注释里出现 --"、"标签未闭合"这类**只在启动时才炸**的低级错误。
 * 该用例正是因线上启动失败（XML 注释含 `--`）后补上的。
 */
class InventoryMapperXmlTest {

    private static final String MAPPER = "src/main/resources/mapper/InventoryMapper.xml";

    @Test
    @DisplayName("XML 可被 MyBatis 解析，且看板统计 SQL 具备售罄率四个计数")
    void statSqlIsParseable() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream in = Files.newInputStream(Path.of(MAPPER))) {
            new XMLMapperBuilder(in, configuration, MAPPER, configuration.getSqlFragments()).parse();
        }

        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.inventory.mapper.InventoryMapper.selectStat");
        String sql = statement.getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ").trim();

        assertThat(sql).contains("count(1) as sku_total");
        assertThat(sql).contains("sku_sold_out", "sku_zero_stock", "low_stock");
        // 售罄必须排除"从未备货"（total=0），否则新品会被算成售罄
        assertThat(sql).contains("total > 0 and available = 0");
        assertThat(sql).contains("from inventory");
    }
}

package com.zhe.mall.order.mapper;

import com.zhe.mall.api.dto.AdminOrderPageQuery;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单 Mapper XML 的离线校验。
 *
 * <p>为什么不用 Spring 上下文：本机命令行环境对 AF_UNIX 回环有限制（见需求文档 12.8），
 * 起完整 Spring 上下文会失败。这里直接用 MyBatis 的 XMLMapperBuilder 解析真实 XML，
 * 断言生成的 SQL 片段，能覆盖"标签写错 / 参数名对不上 / 条件漏写"这类最常见的映射错误。
 */
class OrderMapperXmlTest {

    private static final String ORDER_MAPPER = "src/main/resources/mapper/OrderMapper.xml";
    private static final String ITEM_MAPPER = "src/main/resources/mapper/OrderItemMapper.xml";

    @Test
    @DisplayName("adminPageCondition：无筛选只保留 deleted=0，有筛选才拼条件")
    void adminPageConditionBuildsExpectedSql() throws Exception {
        Configuration configuration = parse(ORDER_MAPPER);

        String noFilter = sqlOf(configuration, "com.zhe.mall.order.mapper.OrderMapper.countAdminPage",
                new AdminOrderPageQuery(null, null, null, null, null, 1, 10));
        assertThat(normalize(noFilter)).isEqualTo("select count(1) from orders WHERE deleted = 0");

        String full = sqlOf(configuration, "com.zhe.mall.order.mapper.OrderMapper.countAdminPage",
                new AdminOrderPageQuery("A001", 10002L, 20,
                        "2026-09-01T00:00", "2026-09-19T23:59:59.999", 1, 10));
        String n = normalize(full);
        assertThat(n).contains("order_no = ?", "user_id = ?", "status = ?");
        assertThat(n).contains("created_at >= ?", "created_at <= ?");
    }

    @Test
    @DisplayName("selectAdminPage：包含排序与分页参数")
    void selectAdminPageHasOrderAndPaging() throws Exception {
        Configuration configuration = parse(ORDER_MAPPER);
        Map<String, Object> params = new HashMap<>();
        params.put("query", new AdminOrderPageQuery(null, null, null, null, null, 2, 20));
        params.put("offset", 20);
        params.put("limit", 20);

        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.order.mapper.OrderMapper.selectAdminPage");
        BoundSql boundSql = statement.getBoundSql(params);
        String n = normalize(boundSql.getSql());

        assertThat(n).contains("from orders");
        assertThat(n).contains("order by created_at desc, order_no desc");
        assertThat(n).contains("limit ? offset ?");
        // 参数顺序：offset 在前、limit 在后（MyBatis 按 #{offset} / #{limit} 出现顺序绑定）
        assertThat(boundSql.getParameterMappings()).hasSize(2);
    }

    @Test
    @DisplayName("selectStatsByOrderNos：按订单号 IN 批量聚合，不逐单查询")
    void itemStatsUsesInClause() throws Exception {
        Configuration configuration = parse(ITEM_MAPPER);
        Map<String, Object> params = Map.of("orderNos", List.of("A001", "A002"));

        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.order.mapper.OrderItemMapper.selectStatsByOrderNos");
        String n = normalize(statement.getBoundSql(params).getSql());

        assertThat(n).contains("from order_item");
        assertThat(n).contains("order_no in ( ? , ? )");
        assertThat(n).contains("group by order_no");
        assertThat(statement.getBoundSql(params).getParameterMappings()).hasSize(2);
    }

    @Test
    @DisplayName("看板 SQL：按日聚合只统计已成交状态，且用半开区间避免跨日重复")
    void dashboardDailyStatsSql() throws Exception {
        Configuration configuration = parse(ORDER_MAPPER);
        Map<String, Object> params = Map.of(
                "start", java.time.LocalDateTime.of(2026, 11, 1, 0, 0),
                "end", java.time.LocalDateTime.of(2026, 11, 2, 0, 0));

        MappedStatement daily = configuration.getMappedStatement(
                "com.zhe.mall.order.mapper.OrderMapper.selectDailyStats");
        String sql = normalize(daily.getBoundSql(params).getSql());
        assertThat(sql).contains("date(paid_at)");
        assertThat(sql).contains("status in (20, 30, 40, 60, 70)");
        assertThat(sql).contains("paid_at >= ?", "paid_at < ?");
        assertThat(sql).contains("group by date(paid_at)");

        MappedStatement counts = configuration.getMappedStatement(
                "com.zhe.mall.order.mapper.OrderMapper.selectDailyOrderCounts");
        String countSql = normalize(counts.getBoundSql(params).getSql());
        assertThat(countSql).contains("date(created_at)");
        assertThat(countSql).contains("created_at >= ?", "created_at < ?");
        assertThat(countSql).doesNotContain("paid_at");
    }

    @Test
    @DisplayName("看板 SQL：状态计数一次 GROUP BY，不做多次 count")
    void dashboardStatusCountsSql() throws Exception {
        Configuration configuration = parse(ORDER_MAPPER);
        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.order.mapper.OrderMapper.selectStatusCounts");
        String sql = normalize(statement.getBoundSql(Map.of()).getSql());

        assertThat(sql).isEqualTo("select status, count(1) as total from orders where deleted = 0 group by status");
    }

    private Configuration parse(String path) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String sqlOf(Configuration configuration, String id, Object query) {
        MappedStatement statement = configuration.getMappedStatement(id);
        return statement.getBoundSql(Map.of("query", query)).getSql();
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

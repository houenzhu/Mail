package com.zhe.mall.pay.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 退款 Mapper XML 的离线校验（与 OrderMapperXmlTest 同一做法）。
 *
 * <p>覆盖最容易出错的三处：待审核单必须参与幂等命中、审核 SQL 必须是 CAS、
 * 驳回必须释放 {@code active_key} 才能让买家重新申请。
 */
class RefundMapperXmlTest {

    private static final String MAPPER = "src/main/resources/mapper/RefundMapper.xml";

    @Test
    @DisplayName("幂等查询包含待审核：0/10/20 都算有效退款单")
    void selectActiveByOrderNoIncludesPendingAudit() throws Exception {
        String sql = normalize(parse(MAPPER)
                .getMappedStatement("com.zhe.mall.pay.mapper.RefundMapper.selectActiveByOrderNo")
                .getBoundSql(Map.of("orderNo", "A001")).getSql());

        assertThat(sql).contains("status in (0, 10, 20)");
        assertThat(sql).contains("order by created_at desc");
    }

    @Test
    @DisplayName("审核通过：CAS 校验 expectStatus，只写审核人不覆盖发起方")
    void markApprovedKeepsOriginalOperator() throws Exception {
        Configuration configuration = parse(MAPPER);
        Map<String, Object> params = new HashMap<>();
        params.put("refundNo", "R1");
        params.put("expectStatus", 0);
        params.put("status", 10);
        params.put("auditedBy", "operator01");
        params.put("auditedById", 10003L);
        params.put("auditedAt", LocalDateTime.of(2026, 11, 4, 10, 0));

        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.pay.mapper.RefundMapper.markApproved");
        String sql = normalize(statement.getBoundSql(params).getSql());

        assertThat(sql).contains("set status = ?", "audited_by = ?", "audited_by_id = ?", "audited_at = ?");
        assertThat(sql).doesNotContain("operator = ?", "operator_id = ?");
        assertThat(sql).contains("where refund_no = ? and status = ?");
        assertThat(sql).doesNotContain("active_key = null");
    }

    @Test
    @DisplayName("驳回：写原因与完成时间，并释放 active_key 允许重新申请")
    void markRejectedReleasesActiveKey() throws Exception {
        Configuration configuration = parse(MAPPER);
        Map<String, Object> params = new HashMap<>();
        params.put("refundNo", "R1");
        params.put("expectStatus", 0);
        params.put("status", 30);
        params.put("failReason", "商品已签收");
        params.put("auditedBy", "operator01");
        params.put("auditedById", 10003L);
        params.put("auditedAt", LocalDateTime.of(2026, 11, 4, 10, 0));
        params.put("finishedAt", LocalDateTime.of(2026, 11, 4, 10, 0));

        MappedStatement statement = configuration.getMappedStatement(
                "com.zhe.mall.pay.mapper.RefundMapper.markRejected");
        String sql = normalize(statement.getBoundSql(params).getSql());

        assertThat(sql).contains("fail_reason = ?", "finished_at = ?", "active_key = null");
        assertThat(sql).contains("where refund_no = ? and status = ?");
    }

    @Test
    @DisplayName("待处理计数 = 待审核 + 退款中，避免漏掉尚未审批的单")
    void countPendingCoversAuditAndRefunding() throws Exception {
        String sql = normalize(parse(MAPPER)
                .getMappedStatement("com.zhe.mall.pay.mapper.RefundMapper.countPending")
                .getBoundSql(Map.of()).getSql());

        assertThat(sql).isEqualTo("select count(1) from refund where status in (0, 10)");
    }

    @Test
    @DisplayName("baseColumns 含审核字段，否则实体永远读不到审核人")
    void baseColumnsIncludeAuditFields() throws Exception {
        String sql = normalize(parse(MAPPER)
                .getMappedStatement("com.zhe.mall.pay.mapper.RefundMapper.selectByRefundNo")
                .getBoundSql(Map.of("refundNo", "R1")).getSql());

        assertThat(sql).contains("audited_by", "audited_by_id", "audited_at");
    }

    private Configuration parse(String path) throws Exception {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            Configuration configuration = new Configuration();
            new XMLMapperBuilder(in, configuration, path, configuration.getSqlFragments()).parse();
            return configuration;
        }
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

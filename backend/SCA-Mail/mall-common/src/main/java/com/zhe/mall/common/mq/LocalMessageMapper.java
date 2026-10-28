package com.zhe.mall.common.mq;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表 Mapper（19.6 / 20.3）。
 *
 * <p><b>为什么用注解而不是 XML</b>：本接口要同时被 mall-order 与 mall-pay 复用，
 * 而两者都配了 {@code mybatis.mapper-locations: classpath*:mapper/*.xml}。
 * 若用 XML，两个模块各放一份同名 namespace 的 XML，一旦有服务同时依赖两者就会
 * "Mapped Statements collection already contains value" 冲突。注解映射随 jar 走，
 * 天然只有一份。
 *
 * <p>表名固定 {@code local_message}：每个库（mall_order / mall_pay）各有一张同构表，
 * 服务只访问自己的库，所以不需要动态表名。
 */
@Mapper
public interface LocalMessageMapper {

    /** 落 Outbox；必须在业务事务内调用，保证与业务写同生共死。 */
    @Insert("""
            insert into local_message
              (id, msg_id, topic, tag, msg_key, biz_type, biz_id, payload, trace_id,
               status, retry_count, next_retry_at, delay_level, owner)
            values
              (#{id}, #{msgId}, #{topic}, #{tag}, #{msgKey}, #{bizType}, #{bizId}, #{payload}, #{traceId},
               #{status}, #{retryCount}, #{nextRetryAt}, #{delayLevel}, #{owner})
            """)
    int insert(LocalMessage message);

    /**
     * 认领一批待投递消息（多实例安全）。
     *
     * <p>认领 = 把 owner 抢占成本实例标识；只有 owner 为 NULL 或已过期未被清理的行才可抢。
     * 这样即使两个实例同时扫描到同一行，也只有一个能更新成功（affected=1）。
     */
    @Update("""
            update local_message
            set owner = #{owner}
            where id = #{id}
              and status = 0
              and (owner is null or owner = #{owner})
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner);

    /** 候选：待发送且已到重试时间。 */
    @Select("""
            select * from local_message
            where status = 0 and next_retry_at <= #{now}
            order by next_retry_at
            limit #{limit}
            """)
    List<LocalMessage> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 投递成功：置已发送（条件里带 status=0，重复投递只会影响 0 行）。 */
    @Update("""
            update local_message
            set status = 1, sent_at = #{sentAt}, error_msg = null, owner = null
            where id = #{id} and status = 0
            """)
    int markSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 投递失败：累加重试次数并推到下次重试时间；达到上限则由调用方改调 {@link #markDead}。
     */
    @Update("""
            update local_message
            set retry_count = retry_count + 1,
                next_retry_at = #{nextRetryAt},
                error_msg = #{errorMsg},
                owner = null
            where id = #{id} and status = 0
            """)
    int markRetry(@Param("id") Long id,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("errorMsg") String errorMsg);

    /** 超过最大重试次数：置死信，等待人工处理（20.3「超过 3 次失败 → 死信 + 告警」）。 */
    @Update("""
            update local_message
            set status = 2,
                retry_count = retry_count + 1,
                error_msg = #{errorMsg},
                owner = null
            where id = #{id} and status = 0
            """)
    int markDead(@Param("id") Long id, @Param("errorMsg") String errorMsg);

    /** 业务键查询，排障与对账用。 */
    @Select("""
            select * from local_message
            where biz_type = #{bizType} and biz_id = #{bizId}
            order by id
            """)
    List<LocalMessage> selectByBiz(@Param("bizType") String bizType, @Param("bizId") String bizId);

    /** 死信条数，供任务打点与告警。 */
    @Select("select count(*) from local_message where status = 2")
    long countDead();
}

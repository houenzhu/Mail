package com.zhe.mall.common.mq;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 消费幂等表 Mapper（19.6「消费」）。
 *
 * <p>三态用法：
 * <ol>
 *   <li>{@link #insertProcessing} 成功 = 首次受理，继续执行业务；抛 DuplicateKeyException = 重复投递，直接跳过；</li>
 *   <li>业务成功 → {@link #markSuccess}；</li>
 *   <li>业务失败 → {@link #deleteByMsgId} 并返回 RECONSUME_LATER，
 *       否则"一次失败"会被唯一键变成"永久跳过"。</li>
 * </ol>
 *
 * <p>与 {@link LocalMessageMapper} 同样用注解映射，理由见该类的注释。
 */
@Mapper
public interface ConsumedMessageMapper {

    /** 认领消息；主键冲突（重复投递）由调用方捕获 DuplicateKeyException。 */
    @Insert("""
            insert into consumed_message (msg_id, topic, consumer, dedup_key, status)
            values (#{msgId}, #{topic}, #{consumer}, #{dedupKey}, 0)
            """)
    int insertProcessing(@Param("msgId") String msgId,
                         @Param("topic") String topic,
                         @Param("consumer") String consumer,
                         @Param("dedupKey") String dedupKey);

    /** 处理成功。 */
    @Update("""
            update consumed_message
            set status = 1, error_msg = null, consumed_at = current_timestamp(3)
            where msg_id = #{msgId}
            """)
    int markSuccess(@Param("msgId") String msgId);

    /** 处理失败：删除认领行，让 broker 重投能重新认领。 */
    @Delete("delete from consumed_message where msg_id = #{msgId} and status = 0")
    int deleteByMsgId(@Param("msgId") String msgId);

    /** 是否已成功消费（排障与对账用）。 */
    @Select("select count(*) from consumed_message where msg_id = #{msgId} and status = 1")
    long countSuccess(@Param("msgId") String msgId);
}

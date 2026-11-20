package com.example.zhihuiqingtu.award.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zhihuiqingtu.award.entity.CreditRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 学分/时长发放记录数据访问层。
 */
public interface CreditRecordRepository extends BaseMapper<CreditRecord> {

    /**
     * 物理删除一条尚未确认发放的记录（撤销签退时回滚发放生成）。
     *
     * <p>这里不用逻辑删除：唯一索引 {@code uk_credit_user_activity}
     * 包含 {@code delete_flag}，同一人同一活动第二次撤销签退时会与
     * 上一条 {@code delete_flag=1} 的历史记录冲突。已发放记录不允许删除。
     *
     * @param recordId 发放记录主键
     * @return 影响行数；0 表示记录不存在或已经确认发放
     */
    @Delete("""
            DELETE FROM credit_record
             WHERE record_id = #{recordId}
               AND verify_status <> 1
            """)
    int deleteUnconfirmedAward(@Param("recordId") Long recordId);
}

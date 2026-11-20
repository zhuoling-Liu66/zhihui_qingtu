package com.example.zhihuiqingtu.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务时序回调工具。
 *
 * <p><b>为什么需要它</b>：Redis（详情缓存、热门榜、名额闸门）不是事务资源，
 * Spring 的 {@code @Transactional} 只管数据库。如果在事务方法内部直接更新 Redis，
 * 会出现在数据库尚未提交、甚至最终回滚的情况下提前改了 Redis 的问题：
 * <ul>
 *   <li>缓存被删后，其他请求在事务提交前回源，会把<b>旧数据</b>重新写回缓存；</li>
 *   <li>事务回滚时热门榜分数已经加过，榜单与实际报名数长期偏离。</li>
 * </ul>
 *
 * <p>因此约定：所有"数据库提交后才应该生效"的外部副作用，都通过
 * {@link #afterCommit(String, Runnable)} 注册。
 *
 * <p><b>异常策略</b>：提交后的回调发生异常时只记录日志，不向外抛出。
 * 此时数据库已经提交成功，若把异常抛给调用方，会让调用方误以为业务失败并重试，
 * 造成重复写入；外部状态（缓存/榜单）的偏差是可以人工修复的次要问题。
 */
@Slf4j
public final class TransactionEvents {

    private TransactionEvents() {
    }

    /**
     * 在当前事务提交成功后执行。
     *
     * <p>没有活动事务时（例如单元测试直接调用服务方法）立即执行，保持调用方行为一致。
     *
     * @param action   动作名称，仅用于日志定位
     * @param runnable 提交后要执行的动作
     */
    public static void afterCommit(String action, Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(action, runnable);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(action, runnable);
            }
        });
    }

    /**
     * 在当前事务回滚后执行（提交成功则不执行）。
     *
     * <p>典型用途：报名时提前在 Redis 扣过名额，但数据库最终回滚（重复报名、
     * 后续步骤抛异常），必须把这一个名额补回去，否则活动明明有空位却一直提示已满。
     *
     * @param action   动作名称，仅用于日志定位
     * @param runnable 回滚后要执行的动作
     */
    public static void afterRollback(String action, Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 没有事务就不存在"回滚"，不能执行补偿动作，否则会凭空多还一个名额
            log.debug("无活动事务，跳过回滚补偿 action={}", action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    runSafely(action, runnable);
                }
            }
        });
    }

    private static void runSafely(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException exception) {
            // 数据库已经提交，这里不能把异常抛回调用方（会被误判为业务失败而重试）
            log.error("事务后置动作执行失败 action={}，数据库状态已提交，请人工核对缓存/榜单/库存", action, exception);
        }
    }
}

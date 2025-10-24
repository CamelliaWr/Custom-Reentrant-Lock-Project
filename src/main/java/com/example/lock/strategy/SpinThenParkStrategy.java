/**
 * 自旋后阻塞策略实现类，提供混合等待机制。
 *
 * 该策略结合了自旋等待和线程阻塞的优点，适用于：
 * - 锁持有时间不确定的场景
 * - 需要在CPU使用和响应性之间平衡的应用
 * - 高并发环境下减少上下文切换开销
 *
 * 实现特点：
 * - 先进行轻量级自旋等待，快速响应短锁持有
 * - 自旋失败后转为线程阻塞，避免CPU浪费
 * - 使用LockSupport.parkNanos()进行精确的时间控制
 * - 支持中断检测，响应线程中断请求
 *
 * 性能优势：
 * - 对于短锁持有，避免昂贵的上下文切换
 * - 对于长锁持有，避免CPU资源浪费
 * - 自适应的等待策略，适应不同的锁竞争模式
 *
 * @author 示例作者
 * @since 1.0
 * @see WaitStrategy
 * @see LockSupport
 * @see Thread#onSpinWait()
 */
package com.example.lock.strategy;

import java.util.concurrent.locks.LockSupport;

/**
 * 自旋后阻塞策略的具体实现。
 *
 * 该策略实现了WaitStrategy接口，通过两阶段等待机制
 * 在响应性和资源利用率之间取得平衡。
 *
 * 等待过程：
 * 1. 执行指定次数的硬件级自旋等待
 * 2. 自旋失败后阻塞线程1微秒
 * 3. 检查线程中断状态
 *
 * 设计权衡：
 * - 自旋时间：平衡响应性和CPU使用
 * - 阻塞时间：避免过长的阻塞延迟
 * - 中断处理：保证响应性和正确性
 */
public class SpinThenParkStrategy implements WaitStrategy {

    /**
     * 自旋次数。
     *
     * 控制第一阶段自旋等待的次数，默认值100次
     * 适用于大多数混合负载场景。
     *
     * 数值选择考虑：
     * - 较小值：更快进入阻塞，减少CPU使用
     * - 较大值：更长的自旋等待，提高短锁响应性
     * - 需要根据锁持有时间和竞争程度调优
     */
    private final int spins;

    /**
     * 默认构造方法。
     *
     * 使用默认的自旋次数（100次）创建混合等待策略。
     * 适用于大多数通用场景，提供平衡的响应性和资源利用率。
     */
    public SpinThenParkStrategy() {
        this(100);
    }

    /**
     * 带参数的构造方法。
     *
     * 允许自定义自旋次数，适用于特定性能调优需求。
     *
     * @param spins 自旋次数，必须为非负数
     * @throws IllegalArgumentException 如果spins < 0
     *
     * @implNote 建议根据锁持有时间的分布特征选择自旋次数
     * @implNote 对于短锁为主的场景，可以增加自旋次数
     */
    public SpinThenParkStrategy(int spins) {
        if (spins < 0) {
            throw new IllegalArgumentException("自旋次数不能为负数");
        }
        this.spins = spins;
    }

    /**
     * 执行等待操作。
     *
     * 实现两阶段等待逻辑：
     * 1. 执行硬件级自旋等待，快速响应短锁释放
     * 2. 自旋失败后阻塞线程，避免CPU资源浪费
     * 3. 检查线程中断状态，保证响应性
     *
     * 阻塞策略：
     * - 使用LockSupport.parkNanos(1_000)阻塞1微秒
     * - 提供比Thread.sleep()更精确的阻塞控制
     * - 避免过长的阻塞时间，保持响应性
     *
     * @throws InterruptedException 如果线程被中断
     *
     * @implNote 自旋和阻塞时间需要根据实际负载调优
     * @implNote LockSupport.parkNanos()提供纳秒级阻塞精度
     */
    @Override
    public void await() throws InterruptedException {
        // 第一阶段：硬件级自旋等待
        for (int i = 0; i < spins; i++) {
            // 处理器级别的自旋优化提示
            Thread.onSpinWait();
        }

        // 第二阶段：线程阻塞，避免CPU浪费
        LockSupport.parkNanos(1_000); // 阻塞1微秒

        // 检查中断状态，响应中断请求
        if (Thread.interrupted()) {
            throw new InterruptedException("等待过程中被中断");
        }
    }
}
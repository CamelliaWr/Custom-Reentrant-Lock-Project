/**
 * 忙等待策略实现类，提供基于自旋的等待机制。
 *
 * 该策略通过CPU自旋等待来避免线程阻塞，适用于：
 * - 锁持有时间很短的场景
 * - 多核处理器环境
 * - 对延迟敏感的高性能应用
 *
 * 实现特点：
 * - 使用Thread.onSpinWait()进行硬件级自旋优化
 * - 可配置的最大自旋次数，避免无限自旋
 * - 自旋结束后主动让出CPU时间片
 * - 支持中断检测，响应线程中断请求
 *
 * 性能考虑：
 * - 自旋会消耗CPU资源，适用于短等待场景
 * - 自旋次数过多可能导致CPU浪费
 * - 在单核处理器上效果不佳
 * - 需要平衡自旋时间和CPU使用率
 *
 * @author 示例作者
 * @since 1.0
 * @see WaitStrategy
 * @see Thread#onSpinWait()
 */
package com.example.lock.strategy;

/**
 * 忙等待策略的具体实现。
 *
 * 该策略实现了WaitStrategy接口，通过自旋等待机制
 * 在锁竞争时避免线程阻塞和上下文切换开销。
 *
 * 自旋过程：
 * 1. 执行指定次数的硬件级自旋等待
 * 2. 自旋结束后主动让出CPU时间片
 * 3. 检查线程中断状态，响应中断请求
 */
public class BusySpinStrategy implements WaitStrategy {

    /**
     * 最大自旋次数。
     *
     * 控制自旋等待的上限，避免无限自旋导致的CPU浪费。
     * 默认值10,000次适用于大多数短等待场景。
     *
     * 数值选择考虑：
     * - 太小：可能频繁让出CPU，增加调度开销
     * - 太大：可能浪费CPU资源，影响系统性能
     * - 需要根据具体应用场景和处理器性能调优
     */
    private final int maxSpins;

    /**
     * 默认构造方法。
     *
     * 使用默认的最大自旋次数（10,000次）创建忙等待策略。
     * 适用于大多数通用场景，提供平衡的性能表现。
     */
    public BusySpinStrategy() {
        this(10_000);
    }

    /**
     * 带参数的构造方法。
     *
     * 允许自定义最大自旋次数，适用于特定性能调优需求。
     *
     * @param maxSpins 最大自旋次数，必须为正数
     * @throws IllegalArgumentException 如果maxSpins <= 0
     *
     * @implNote 建议使用性能测试确定最佳自旋次数
     * @implNote 不同处理器架构可能需要不同的自旋参数
     */
    public BusySpinStrategy(int maxSpins) {
        if (maxSpins <= 0) {
            throw new IllegalArgumentException("最大自旋次数必须为正数");
        }
        this.maxSpins = maxSpins;
    }

    /**
     * 执行等待操作。
     *
     * 实现忙等待逻辑：
     * 1. 执行硬件级自旋等待（Thread.onSpinWait）
     * 2. 达到最大自旋次数后主动让出CPU
     * 3. 检查线程中断状态
     *
     * 硬件优化：
     * - Thread.onSpinWait()提供处理器级别的自旋优化
     * - 在某些架构上可能触发功耗优化
     * - 为处理器提供等待提示，改善流水线效率
     *
     * @throws InterruptedException 如果线程被中断
     *
     * @implNote 自旋过程中不响应中断，结束后统一检查
     * @implNote Thread.yield()让出CPU时间片，避免长时间占用
     */
    @Override
    public void await() throws InterruptedException {
        // 执行硬件级自旋等待
        int spins = 0;
        while (spins++ < maxSpins) {
            // 处理器级别的自旋优化提示
            Thread.onSpinWait();
        }

        // 自旋结束后让出CPU时间片
        Thread.yield();

        // 检查中断状态，响应中断请求
        if (Thread.interrupted()) {
            throw new InterruptedException("等待过程中被中断");
        }
    }
}
/**
 * 锁性能指标统计类，用于收集和监控锁的使用情况。
 *
 * 该类提供了简单的锁性能指标收集功能，主要用于：
 * - 锁获取次数统计
 * - 锁竞争情况统计
 * - 性能分析和调优
 *
 * 目前这是一个演示版本，指标收集功能还未完全集成到所有锁操作路径中。
 * 未来可扩展的功能包括：
 * - 平均等待时间
 * - 最大等待时间
 * - 锁持有时间
 * - 线程等待队列长度
 * - 更多详细的性能指标
 *
 * 所有方法都是线程安全的，使用了synchronized关键字确保
 * 多线程环境下的正确性。
 *
 * 使用示例：
 * <pre>
 * LockMetrics metrics = new LockMetrics();
 * // 在锁获取时调用
 * metrics.incAcq();
 * // 在发生锁竞争时调用  
 * metrics.incContended();
 * // 获取统计信息
 * long acquisitions = metrics.getAcquisitions();
 * long contended = metrics.getContended();
 * </pre>
 *
 * @author 示例作者
 * @since 1.0
 * @see AbstractQueuedLock
 */
package com.example.lock.core;

/**
 * 简单度量占位（可扩展）。当前为演示，未集成到所有路径。
 *
 * 该类提供了基础的锁性能监控功能，包括：
 * - 锁获取次数统计：记录成功获取锁的总次数
 * - 锁竞争统计：记录发生锁竞争的次数
 *
 * 设计考虑：
 * - 使用简单的long类型计数器，避免性能开销
 * - 所有方法同步，确保线程安全
 * - 轻量级实现，不影响锁的核心性能
 *
 * 未来扩展计划：
 * - 支持更细粒度的指标分类
 * - 提供指标导出和监控集成
 * - 支持性能阈值告警
 */
public class LockMetrics {

    /**
     * 锁获取总次数计数器。
     * 记录成功获取锁的次数，包括首次获取和重入获取。
     */
    private long acquisitions = 0;

    /**
     * 锁竞争次数计数器。
     * 记录线程在获取锁时发生竞争的次数，用于衡量锁的争用程度。
     */
    private long contended = 0;

    /**
     * 增加锁获取次数。
     * 每次成功获取锁时调用，包括锁的重入。
     *
     * 线程安全：使用synchronized确保多线程环境下的正确性
     */
    public synchronized void incAcq() { acquisitions++; }

    /**
     * 增加锁竞争次数。
     * 当线程发现锁已被其他线程持有，需要等待时调用。
     *
     * 线程安全：使用synchronized确保多线程环境下的正确性
     */
    public synchronized void incContended() { contended++; }

    /**
     * 获取锁获取总次数。
     *
     * @return 成功获取锁的总次数
     */
    public synchronized long getAcquisitions() { return acquisitions; }

    /**
     * 获取锁竞争次数。
     *
     * @return 发生锁竞争的总次数
     */
    public synchronized long getContended() { return contended; }

    /**
     * 获取锁竞争率。
     *
     * 计算锁竞争次数占总获取次数的比例，反映锁的争用程度。
     * 竞争率越高，说明锁的争用越激烈。
     *
     * @return 竞争率（0.0到1.0之间），如果没有获取记录返回0.0
     */
    public synchronized double getContentionRate() {
        return acquisitions > 0 ? (double) contended / acquisitions : 0.0;
    }

    /**
     * 重置所有计数器。
     * 将获取次数和竞争次数都重置为0。
     *
     * 使用场景：开始新的性能测试或监控周期时
     */
    public synchronized void reset() {
        acquisitions = 0;
        contended = 0;
    }

    /**
     * 获取统计信息摘要。
     *
     * @return 包含关键指标的字符串表示
     */
    @Override
    public synchronized String toString() {
        return String.format("LockMetrics{acquisitions=%d, contended=%d, contentionRate=%.4f}",
                acquisitions, contended, getContentionRate());
    }
}
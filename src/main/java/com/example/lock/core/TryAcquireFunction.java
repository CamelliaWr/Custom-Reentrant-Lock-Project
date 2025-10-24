/**
 * 尝试获取锁的函数式接口，用于封装锁获取逻辑。
 *
 * 该接口定义了锁获取的基本操作，作为函数式接口可以与Lambda表达式一起使用。
 * 主要用于队列策略中，将具体的锁获取逻辑传递给队列实现。
 *
 * 设计目的：
 * - 解耦锁获取逻辑与队列管理逻辑
 * - 支持不同的锁实现策略
 * - 提供函数式编程支持
 *
 * 使用场景：
 * - CLH队列和MCS队列的锁获取操作
 * - 超时获取锁的实现
 * - 条件等待后的锁重获取
 *
 * 示例：
 * <pre>
 * TryAcquireFunction tryAcquire = (Thread current) -> {
 *     if (current == owner.get()) {
 *         holdCount++;
 *         return true;
 *     }
 *     if (owner.compareAndSet(null, current)) {
 *         holdCount = 1;
 *         return true;
 *     }
 *     return false;
 * };
 * </pre>
 *
 * @author 示例作者
 * @since 1.0
 * @see java.util.function.Function
 * @see AbstractQueuedLock
 */
package com.example.lock.core;

/**
 * 函数式接口，用于定义尝试获取锁的操作。
 *
 * 该接口使用@FunctionalInterface注解，确保其函数式接口特性，
 * 可以与Lambda表达式和方法引用一起使用。
 *
 * 主要用途：
 * - 在队列策略中传递锁获取逻辑
 * - 支持不同的锁实现策略
 * - 提供灵活的锁获取机制
 *
 * 线程安全性：实现类必须确保tryAcquire方法是线程安全的
 */
@FunctionalInterface
public interface TryAcquireFunction {

    /**
     * 尝试让指定线程获取锁。
     *
     * 该方法封装了具体的锁获取逻辑，包括：
     * - 检查锁的当前状态
     * - 尝试原子地获取锁
     * - 处理可重入逻辑
     * - 更新内部状态
     *
     * @param current 尝试获取锁的线程，通常为当前线程
     * @return true如果成功获取锁，false如果锁不可用或被其他线程持有
     *
     * @implNote 实现必须考虑：
     * - 线程安全性（通常使用CAS操作）
     * - 可重入性支持
     * - 性能优化
     *
     * @example
     * // Lambda表达式实现
     * TryAcquireFunction tryAcquire = thread -> {
     *     return lock.compareAndSet(null, thread);
     * };
     *
     * @example
     * // 方法引用实现
     * TryAcquireFunction tryAcquire = this::tryAcquireLock;
     */
    boolean tryAcquire(Thread current);
}

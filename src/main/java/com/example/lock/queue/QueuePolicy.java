/**
 * 队列策略接口，定义了锁排队和获取的通用契约。
 *
 * 该接口抽象了不同锁实现（如CLH锁、MCS锁、自定义锁）的排队机制，
 * 提供了统一的锁获取接口，支持公平性、超时和中断处理。
 *
 * 设计目标：
 * - 支持多种锁算法实现（CLH、MCS、自定义）
 * - 提供统一的锁获取和排队接口
 * - 支持超时机制，防止无限等待
 * - 支持中断响应，提高系统响应性
 * - 提供后继唤醒机制，优化性能
 *
 * 实现要求：
 * - 线程安全：支持多线程并发访问
 * - 公平性：保证锁获取的公平顺序
 * - 性能：最小化锁竞争和上下文切换
 * - 可扩展：支持新的锁算法实现
 *
 * 使用场景：
 * - 自定义锁实现的核心排队机制
 * - 高性能并发数据结构
 * - 线程同步和协调
 * - 资源访问控制
 *
 * @author 示例作者
 * @since 1.0
 * @see TryAcquireFunction
 * @see WaitStrategy
 * @see CLHQueue
 * @see MCSQueue
 */
package com.example.lock.queue;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.strategy.WaitStrategy;

/**
 * 锁排队策略的抽象接口。
 *
 * 该接口定义了锁实现中排队机制的核心操作，包括：
 * - 锁对象关联：建立队列与具体锁实例的关系
 * - 入队获取：将线程加入等待队列并尝试获取锁
 * - 超时获取：支持超时机制的锁获取
 * - 后继唤醒：优化锁释放时的线程通知
 *
 * 实现类需要提供具体的排队算法，如CLH、MCS或其他自定义算法。
 */
public interface QueuePolicy {

    /**
     * 设置关联的锁对象。
     *
     * 建立队列策略与具体锁实例的关联关系，某些实现可能需要
     * 访问锁的内部状态或方法。
     *
     * @param lock 关联的锁对象，可以是任何类型的锁实现
     *
     * @implSpec 实现类可以选择使用或忽略该参数，取决于具体算法需求
     * @implNote 对于不需要锁对象引用的实现，可以实现为空方法
     */
    void setLock(Object lock);

    /**
     * 入队并获取锁（无超时版本）。
     *
     * 将当前线程加入等待队列，并尝试获取锁。该方法会阻塞直到
     * 成功获取锁或被中断。
     *
     * 实现要求：
     * - 保证线程安全的入队操作
     * - 支持公平性，避免线程饥饿
     * - 响应中断，抛出InterruptedException
     * - 使用提供的等待策略进行自旋或阻塞
     *
     * @param tryAcquire 锁获取函数，用于实际获取锁
     * @param waitStrategy 等待策略，控制等待行为（自旋、阻塞等）
     * @throws InterruptedException 如果等待过程中被中断
     *
     * @implSpec 实现类需要保证FIFO顺序和线程安全性
     * @implNote 可以使用自旋、阻塞或混合策略进行等待
     */
    void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException;

    /**
     * 入队并获取锁（带超时版本）。
     *
     * 将当前线程加入等待队列，并在指定时间内尝试获取锁。
     * 如果在超时时间内无法获取锁，则返回false。
     *
     * 实现要求：
     * - 支持精确的超时控制
     * - 超时后清理队列状态
     * - 保证线程安全和公平性
     * - 响应中断，抛出InterruptedException
     *
     * @param tryAcquire 锁获取函数，用于实际获取锁
     * @param nanos 超时时间（纳秒）
     * @param waitStrategy 等待策略，控制等待行为
     * @return true 成功获取锁，false 超时失败
     * @throws InterruptedException 如果等待过程中被中断
     *
     * @implSpec 超时时间必须精确到纳秒级别
     * @implNote 需要在关键等待点检查超时，避免无效等待
     */
    boolean tryAcquireWithTimeout(TryAcquireFunction tryAcquire, long nanos, WaitStrategy waitStrategy) throws InterruptedException;

    /**
     * 唤醒后继节点。
     *
     * 在锁释放时，通知等待队列中的后继线程，优化锁获取的
     * 延迟和吞吐量。不同的实现可以采用不同的唤醒策略。
     *
     * 实现策略：
     * - 直接唤醒特定的后继线程
     * - 广播唤醒所有等待线程
     * - 使用条件变量进行精确唤醒
     * - 结合自旋和阻塞的混合策略
     *
     * @implSpec 实现类应该优化唤醒性能，避免不必要的线程调度
     * @implNote 可以结合具体的排队算法选择最优的唤醒策略
     */
    void unparkSuccessor();
}
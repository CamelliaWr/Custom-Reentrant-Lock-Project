/**
 * MCS队列策略实现类，提供基于MCS算法的锁排队机制。
 *
 * MCS（Mellor-Crummey and Scott）锁是一种基于显式链表的高性能自旋锁实现，
 * 通过维护显式的等待线程队列和本地自旋变量，特别适合NUMA架构的多核处理器。
 *
 * 核心特性：
 * - 每个线程在本地变量上自旋，减少远程内存访问
 * - 显式链表结构，支持精确的后继通知
 * - 支持公平锁获取，保证先进先出顺序
 * - 提供超时机制，防止无限等待
 * - 线程本地存储节点，减少对象创建开销
 *
 * 实现原理：
 * 1. 线程通过原子操作将自己添加到队列尾部
 * 2. 如果有前驱节点，等待其在本地变量上自旋
 * 3. 前驱节点释放锁时，直接设置后继节点的locked状态
 * 4. 支持超时取消机制，避免死锁
 *
 * 与CLH的区别：
 * - MCS使用显式后继指针，CLH使用隐式链表
 * - MCS在本地变量自旋，CLH在前驱节点上自旋
 * - MCS更适合NUMA架构，减少远程内存访问
 *
 * @author 示例作者
 * @since 1.0
 * @see QueuePolicy
 * @see MCSNode
 * @see TryAcquireFunction
 */
package com.example.lock.queue.mcs;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * MCS队列策略的具体实现。
 *
 * 该类实现了QueuePolicy接口，提供MCS算法的锁排队和获取机制。
 * 通过维护一个显式的线程等待队列和本地自旋变量，确保高性能和可扩展性。
 */
public class MCSQueue implements QueuePolicy {

    /**
     * 队列尾部指针。
     *
     * 使用AtomicReference保证多线程环境下的原子性操作，
     * 通过getAndSet方法实现线程安全的队列尾部更新。
     * 初始为null，表示队列为空。
     */
    private final AtomicReference<MCSNode> tail = new AtomicReference<>();

    /**
     * 线程本地节点存储。
     *
     * 每个线程拥有独立的MCSNode实例，避免：
     * - 频繁的节点创建和销毁
     * - 多线程间的节点竞争
     * - 内存分配开销
     *
     * ThreadLocal确保每个线程获取自己的节点副本。
     */
    private final ThreadLocal<MCSNode> myNode = ThreadLocal.withInitial(MCSNode::new);

    /**
     * 设置关联的锁对象（空实现）。
     *
     * MCS队列实现不需要显式的锁对象引用，
     * 节点本身包含了足够的状态信息。
     *
     * @param lock 锁对象（未使用）
     */
    @Override
    public void setLock(Object lock) { }

    /**
     * 入队并获取锁（无超时版本）。
     *
     * 实现步骤：
     * 1. 获取当前线程的MCS节点
     * 2. 原子地将节点添加到队列尾部
     * 3. 如果有前驱节点，建立连接并等待本地变量
     * 4. 尝试获取实际锁
     * 5. 标记当前节点为已释放状态
     *
     * @param tryAcquire 锁获取函数，用于实际获取锁
     * @param waitStrategy 等待策略，控制自旋等待行为
     * @throws InterruptedException 如果等待过程中被中断
     *
     * @implNote 使用本地自旋变量，减少远程内存访问
     * @implNote 保证FIFO公平性，先排队先获取锁
     */
    @Override
    public void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException {
        // 获取当前线程的MCS节点
        MCSNode node = myNode.get();

        // 原子地将当前节点添加到队列尾部，并获取前驱节点
        MCSNode pred = tail.getAndSet(node);

        // 如果存在前驱节点，建立连接并等待
        if (pred != null) {
            // 建立前驱到当前节点的连接
            pred.next = node;
            // 在本地变量上自旋等待（MCS核心特性）
            while (node.locked) waitStrategy.await();
        }

        // 前驱节点通知后，尝试获取实际锁
        while (!tryAcquire.tryAcquire(Thread.currentThread())) Thread.onSpinWait();

        // 成功获取锁后，标记当前节点为已释放状态
        node.locked = false;
    }

    /**
     * 入队并获取锁（带超时版本）。
     *
     * 与无超时版本类似，但增加了超时检测机制：
     * - 计算截止时间
     * - 在等待过程中检查超时
     * - 超时后取消节点并返回false
     *
     * @param tryAcquire 锁获取函数，用于实际获取锁
     * @param nanos 超时时间（纳秒）
     * @param waitStrategy 等待策略，控制自旋等待行为
     * @return true 成功获取锁，false 超时失败
     * @throws InterruptedException 如果等待过程中被中断
     *
     * @implNote 在关键等待点检查超时，避免无效等待
     * @implNote 超时后清理节点状态，维护队列完整性
     */
    @Override
    public boolean tryAcquireWithTimeout(TryAcquireFunction tryAcquire, long nanos, WaitStrategy waitStrategy) throws InterruptedException {
        // 计算截止时间
        long deadline = System.nanoTime() + nanos;

        // 获取当前线程的MCS节点
        MCSNode node = myNode.get();

        // 原子地将当前节点添加到队列尾部
        MCSNode pred = tail.getAndSet(node);

        // 等待前驱节点通知（带超时检测）
        if (pred != null) {
            // 建立前驱到当前节点的连接
            pred.next = node;
            // 在本地变量上自旋等待，超时则取消
            while (node.locked) {
                if (System.nanoTime() > deadline) {
                    cancel(node);
                    return false;
                }
                waitStrategy.await();
            }
        }

        // 尝试获取实际锁（带超时检测）
        while (!tryAcquire.tryAcquire(Thread.currentThread())) {
            if (System.nanoTime() > deadline) {
                cancel(node);
                return false;
            }
            Thread.onSpinWait();
        }

        // 成功获取锁
        node.locked = false;
        return true;
    }

    /**
     * 唤醒后继节点。
     *
     * 获取当前尾部节点，如果存在且关联线程不为null，
     * 使用LockSupport.unpark()唤醒其关联的线程。
     *
     * MCS实现中，锁释放时可以直接通过next指针通知后继节点，
     * 这个方法提供了额外的唤醒机制作为补充。
     *
     * @implNote 用于优化锁释放时的通知机制
     * @implNote 直接唤醒尾部节点的线程，简化通知逻辑
     */
    @Override
    public void unparkSuccessor() {
        // 获取当前尾部节点
        MCSNode t = tail.get();
        // 如果存在且关联线程有效，则唤醒
        if (t != null && t.thread != null) LockSupport.unpark(t.thread);
    }

    /**
     * 取消节点并维护队列完整性。
     *
     * 在超时或中断情况下，需要清理节点状态：
     * 1. 尝试将尾部指针重置为null（如果是当前节点）
     * 2. 清理节点的next引用，帮助GC
     *
     * @param node 要取消的节点
     *
     * @implNote 使用CAS操作确保线程安全的尾部更新
     * @implNote 简化取消逻辑，MCS结构相对简单
     */
    private void cancel(MCSNode node) {
        // 尝试将尾部指针重置（如果是当前节点）
        if (tail.get() == node) tail.compareAndSet(node, null);
        // 清理next引用，帮助GC
        node.next = null;
    }
}
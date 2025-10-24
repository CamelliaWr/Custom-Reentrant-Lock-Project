/**
 * CLH队列策略实现类，提供基于CLH算法的锁排队机制。
 *
 * CLH（Craig, Landin, and Hagersten）锁是一种基于链表的高性能自旋锁实现，
 * 通过维护隐式的等待线程队列来避免缓存一致性流量，特别适合多核处理器环境。
 *
 * 核心特性：
 * - 每个线程拥有独立的节点，避免伪共享问题
 * - 基于前驱节点状态进行自旋等待，减少内存访问
 * - 支持公平锁获取，保证先进先出顺序
 * - 提供超时机制，防止无限等待
 * - 线程本地存储节点，减少对象创建开销
 *
 * 实现原理：
 * 1. 线程通过原子操作将自己添加到队列尾部
 * 2. 监视前驱节点的locked状态进行自旋等待
 * 3. 前驱节点释放锁时，当前节点停止自旋并尝试获取锁
 * 4. 支持超时取消机制，避免死锁
 *
 * @author 示例作者
 * @since 1.0
 * @see QueuePolicy
 * @see CLHNode
 * @see TryAcquireFunction
 */
package com.example.lock.queue.clh;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * CLH队列策略的具体实现。
 *
 * 该类实现了QueuePolicy接口，提供CLH算法的锁排队和获取机制。
 * 通过维护一个隐式的线程等待队列，确保锁获取的公平性和高性能。
 */
public class CLHQueue implements QueuePolicy {

    /**
     * 队列尾部指针。
     *
     * 使用AtomicReference保证多线程环境下的原子性操作，
     * 通过getAndSet方法实现线程安全的队列尾部更新。
     * 初始为null，表示队列为空。
     */
    private final AtomicReference<CLHNode> tail = new AtomicReference<>();

    /**
     * 线程本地节点存储。
     *
     * 每个线程拥有独立的CLHNode实例，避免：
     * - 频繁的节点创建和销毁
     * - 多线程间的节点竞争
     * - 内存分配开销
     *
     * ThreadLocal确保每个线程获取自己的节点副本。
     */
    private final ThreadLocal<CLHNode> myNode = ThreadLocal.withInitial(CLHNode::new);

    /**
     * 设置关联的锁对象（空实现）。
     *
     * CLH队列实现不需要显式的锁对象引用，
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
     * 1. 获取当前线程的CLH节点
     * 2. 原子地将节点添加到队列尾部
     * 3. 如果有前驱节点，等待其释放锁
     * 4. 尝试获取实际锁
     * 5. 标记当前节点为已释放状态
     *
     * @param tryAcquire 锁获取函数，用于实际获取锁
     * @param waitStrategy 等待策略，控制自旋等待行为
     * @throws InterruptedException 如果等待过程中被中断
     *
     * @implNote 使用忙等待和自旋优化，避免线程阻塞
     * @implNote 保证FIFO公平性，先排队先获取锁
     */
    @Override
    public void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException {
        // 获取当前线程的CLH节点
        CLHNode node = myNode.get();

        // 原子地将当前节点添加到队列尾部，并获取前驱节点
        CLHNode pred = tail.getAndSet(node);

        // 如果存在前驱节点，需要等待其释放锁
        if (pred != null) {
            // 建立双向链表连接
            node.prev = pred;
            pred.next = node;
            // 自旋等待前驱节点释放锁
            while (pred.locked) {
                waitStrategy.await();
            }
        }

        // 前驱节点释放后，尝试获取实际锁
        while (!tryAcquire.tryAcquire(Thread.currentThread())) {
            Thread.onSpinWait();
        }

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

        // 获取当前线程的CLH节点
        CLHNode node = myNode.get();

        // 原子地将当前节点添加到队列尾部
        CLHNode pred = tail.getAndSet(node);

        // 等待前驱节点释放锁（带超时检测）
        if (pred != null) {
            // 建立双向链表连接
            node.prev = pred;
            pred.next = node;
            // 自旋等待前驱节点，超时则取消
            while (pred.locked) {
                if (System.nanoTime() > deadline) {
                    cancelNode(node);
                    return false;
                }
                waitStrategy.await();
            }
        }

        // 尝试获取实际锁（带超时检测）
        while (!tryAcquire.tryAcquire(Thread.currentThread())) {
            if (System.nanoTime() > deadline) {
                cancelNode(node);
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
     * 从队列头部开始查找第一个有效的后继节点，
     * 并使用LockSupport.unpark()唤醒其关联的线程。
     *
     * 实现逻辑：
     * 1. 获取当前尾部节点
     * 2. 向前遍历到队列头部
     * 3. 找到第一个有效的后继节点
     * 4. 唤醒该节点的线程
     *
     * @implNote 用于优化锁释放时的通知机制
     * @implNote 避免不必要的线程唤醒，提高性能
     */
    @Override
    public void unparkSuccessor() {
        // 获取当前尾部节点
        CLHNode t = tail.get();
        if (t == null) return;

        // 向前遍历到队列头部
        CLHNode cur = t;
        while (cur.prev != null) cur = cur.prev;

        // 获取头部节点的后继
        CLHNode succ = cur.next;

        // 唤醒后继节点的线程（如果存在）
        if (succ != null) LockSupport.unpark(succ.thread);
    }

    /**
     * 取消节点并维护队列完整性。
     *
     * 在超时或中断情况下，需要清理节点状态：
     * 1. 尝试将尾部指针回退到前驱节点
     * 2. 修复双向链表连接
     * 3. 清理节点的引用，帮助GC
     *
     * @param node 要取消的节点
     *
     * @implNote 使用CAS操作确保线程安全的尾部更新
     * @implNote 维护队列的链表完整性，避免断链
     */
    private void cancelNode(CLHNode node) {
        // 尝试将尾部指针回退到前驱节点
        tail.compareAndSet(node, node.prev);

        // 修复链表连接
        if (node.prev != null) node.prev.next = node.next;

        // 清理节点引用
        node.prev = null;
        node.next = null;
    }
}

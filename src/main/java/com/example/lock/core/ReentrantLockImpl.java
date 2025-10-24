/**
 * 可重入锁的具体实现类，继承自AbstractQueuedLock。
 *
 * 该类提供了可重入锁的核心实现，通过组合不同的队列策略和等待策略，
 * 可以创建出具有不同特性的锁实现。
 *
 * 主要特性：
 * - 可重入性：同一线程可以多次获取同一个锁
 * - 策略灵活：支持CLH、MCS等不同队列策略
 * - 等待策略：支持自旋后阻塞、忙等待等不同策略
 * - 公平性支持：可选择公平或非公平模式
 *
 * 设计模式：该类使用了模板方法模式，继承了AbstractQueuedLock的
 * 基本框架，实现了具体的锁获取逻辑。
 *
 * 使用示例：
 * <pre>
 * // 创建CLH公平锁
 * MyLock lock = new ReentrantLockImpl(new CLHQueue(), true, new SpinThenParkStrategy());
 *
 * lock.lock();
 * try {
 *     // 临界区代码
 * } finally {
 *     lock.unlock();
 * }
 * </pre>
 *
 * @author 示例作者
 * @since 1.0
 * @see AbstractQueuedLock
 * @see QueuePolicy
 * @see WaitStrategy
 */
package com.example.lock.core;

import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

public class ReentrantLockImpl extends AbstractQueuedLock {

    /**
     * 构造方法，创建可重入锁实例。
     *
     * @param queue 队列策略实现，决定线程排队和唤醒机制
     * @param fair 是否公平锁。true表示公平锁，按照请求顺序获取锁；
     *             false表示非公平锁，允许插队
     * @param waitStrategy 等待策略，决定线程在获取锁失败时的等待方式
     * @throws NullPointerException 如果queue或waitStrategy为null
     *
     * @example
     * // 创建MCS非公平锁，使用忙等待策略
     * MyLock lock = new ReentrantLockImpl(new MCSQueue(), false, new BusySpinStrategy());
     */
    public ReentrantLockImpl(QueuePolicy queue, boolean fair, WaitStrategy waitStrategy) {
        super(queue, fair, waitStrategy);
    }

    /**
     * 尝试获取锁的核心实现。
     *
     * 实现逻辑：
     * 1. 检查当前线程是否已经持有锁（可重入）
     * 2. 如果锁空闲，尝试原子地获取锁
     * 3. 更新持有计数
     *
     * 这是锁获取的最基本操作，不考虑排队和等待。
     *
     * @param current 当前尝试获取锁的线程
     * @return true如果成功获取锁，false如果锁不可用
     *
     * @implNote 该方法必须是线程安全的，通常使用CAS操作
     */
    @Override
    protected boolean tryAcquire(Thread current) {
        // 检查可重入性：如果当前线程已经持有锁，增加持有计数
        if (current == owner.get()) {
            holdCount++;
            return true;
        }
        // 尝试原子地获取空闲锁
        if (owner.compareAndSet(null, current)) {
            holdCount = 1;
            return true;
        }
        // 锁已被其他线程持有
        return false;
    }

    /**
     * 排队获取锁的实现。
     *
     * 当快速获取失败时，将当前线程加入等待队列并阻塞直到获取锁。
     * 该方法委托给队列策略的具体实现。
     *
     * @throws InterruptedException 如果在等待过程中被中断
     * @implSpec 子类必须实现该方法，提供具体的排队和阻塞逻辑
     */
    @Override
    protected void enqueueAndAcquire() throws InterruptedException {
        // 使用队列策略进行排队获取，传入当前锁的获取逻辑和等待策略
        queue.enqueueAndAcquire(this::tryAcquire, waitStrategy);
    }

    /**
     * 超时获取锁的实现。
     *
     * 在指定时间内尝试获取锁，支持超时和中断。
     * 该方法委托给队列策略的具体实现。
     *
     * @param nanos 等待的最大时间（纳秒）
     * @return true如果在指定时间内成功获取锁，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     * @implSpec 子类必须实现该方法，提供具体的超时获取逻辑
     */
    @Override
    protected boolean tryAcquireWithTimeout(long nanos) throws InterruptedException {
        // 使用队列策略进行超时获取，传入当前锁的获取逻辑、超时时间和等待策略
        return queue.tryAcquireWithTimeout(this::tryAcquire, nanos, waitStrategy);
    }
}

/**
 * 抽象队列锁基类，提供了可重入锁的核心实现框架。
 *
 * 该类实现了MyLock接口，提供了可重入锁的基本功能，包括：
 * - 锁的获取和释放（支持可重入）
 * - 中断响应和超时获取
 * - 状态查询和条件变量支持
 * - 公平性和非公平性支持
 *
 * 该类使用了模板方法模式，将具体的队列操作委托给子类实现，
 * 支持CLH和MCS等不同队列策略。
 *
 * 线程安全性：该类是线程安全的，使用了原子操作和volatile变量
 * 来确保多线程环境下的正确性。
 *
 * @author 示例作者
 * @since 1.0
 * @see MyLock
 * @see QueuePolicy
 * @see WaitStrategy
 */
package com.example.lock.core;

import com.example.lock.api.MyLock;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractQueuedLock implements MyLock {

    /**
     * 当前锁的持有者线程，使用原子引用确保线程安全。
     * null表示锁未被任何线程持有。
     */
    protected final AtomicReference<Thread> owner = new AtomicReference<>();

    /**
     * 当前线程对锁的持有计数，支持可重入特性。
     * 使用volatile确保多线程间的可见性。
     */
    protected volatile int holdCount = 0;

    /**
     * 队列策略，定义了线程排队和唤醒的具体实现。
     * 可以是CLH队列或MCS队列等。
     */
    protected final QueuePolicy queue;

    /**
     * 公平性标志。true表示公平锁，按照请求顺序获取锁；
     * false表示非公平锁，允许插队。
     */
    protected final boolean fair;

    /**
     * 等待策略，定义了线程在获取锁失败时的等待方式。
     * 可以是自旋后阻塞或忙等待等策略。
     */
    protected final WaitStrategy waitStrategy;

    /**
     * 构造方法，初始化锁的基本属性。
     *
     * @param queue 队列策略实现
     * @param fair 是否公平锁
     * @param waitStrategy 等待策略实现
     * @throws NullPointerException 如果queue或waitStrategy为null
     */
    protected AbstractQueuedLock(QueuePolicy queue, boolean fair, WaitStrategy waitStrategy) {
        this.queue = queue;
        this.fair = fair;
        this.waitStrategy = waitStrategy;
        this.queue.setLock(this);
    }

    /**
     * 获取锁的基本实现。支持可重入和非公平插队。
     *
     * 实现逻辑：
     * 1. 检查当前线程是否已经持有锁（可重入）
     * 2. 如果是非公平锁，尝试直接获取（插队）
     * 3. 如果上述都失败，进入队列等待
     *
     * 注意：该方法不响应中断，中断只会设置中断状态
     */
    @Override
    public void lock() {
        Thread current = Thread.currentThread();
        // 检查可重入性
        if (current == owner.get()) {
            holdCount++;
            return;
        }
        // 非公平锁的插队尝试
        if (!fair && owner.compareAndSet(null, current)) {
            holdCount = 1;
            return;
        }
        // 进入队列等待
        try {
            enqueueAndAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 可中断地获取锁。与lock()类似，但在等待过程中可以响应中断。
     *
     * 如果在等待获取锁的过程中被中断，将抛出InterruptedException。
     * 该方法确保了锁获取操作的可中断性。
     *
     * @throws InterruptedException 如果在等待过程中被中断
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return; }
        if (!fair && owner.compareAndSet(null, current)) { holdCount = 1; return; }
        enqueueAndAcquire();
    }

    /**
     * 尝试获取锁的非阻塞版本。
     *
     * 如果锁立即可用则获取锁并返回true；否则立即返回false，不阻塞线程。
     * 适用于需要避免线程阻塞的场景。
     *
     * @return true如果成功获取锁，false如果锁不可用
     */
    @Override
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return true; }
        if (owner.compareAndSet(null, current)) { holdCount = 1; return true; }
        return false;
    }

    /**
     * 在指定时间内尝试获取锁。
     *
     * 如果锁在给定时间内变得可用，则获取锁并返回true；
     * 否则在超时后返回false。支持中断响应。
     *
     * @param time 等待的最大时间
     * @param unit 时间单位
     * @return true如果在指定时间内成功获取锁，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(time);
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return true; }
        if (owner.compareAndSet(null, current)) { holdCount = 1; return true; }
        return tryAcquireWithTimeout(nanos);
    }

    /**
     * 释放锁的实现。
     *
     * 每次调用都会减少持有计数，当持有计数降为0时，锁被完全释放。
     * 只有锁的持有者才能调用此方法。
     *
     * @throws IllegalMonitorStateException 如果当前线程不是锁的持有者
     */
    @Override
    public void unlock() {
        Thread current = Thread.currentThread();
        if (current != owner.get()) throw new IllegalMonitorStateException("Not owner");

        if (holdCount > 1) { holdCount--; return; }

        // last release
        holdCount = 0;
        owner.set(null);
        queue.unparkSuccessor();
    }

    /**
     * 创建与此锁关联的新的Condition实例。
     *
     * 返回的Condition实例支持类似Object监视器方法的功能，
     * 但提供了更丰富的线程协调功能。
     *
     * @return 与此锁关联的新Condition实例
     */
    @Override
    public java.util.concurrent.locks.Condition newCondition() {
        return new ConditionObject(this);
    }

    /**
     * 查询此锁是否被任何线程持有。
     *
     * 这是一个快照方法，结果可能在返回后就过时。
     * 主要用于监控和调试目的。
     *
     * @return true如果有任何线程持有此锁，false如果锁未被持有
     */
    @Override
    public boolean isLocked() {
        return owner.get() != null;
    }

    /**
     * 查询当前线程是否持有此锁。
     *
     * 用于检查当前执行线程是否是锁的持有者。
     *
     * @return true如果当前线程持有此锁，false否则
     */
    @Override
    public boolean isHeldByCurrentThread() {
        return owner.get() == Thread.currentThread();
    }

    /**
     * 获取当前线程对此锁的持有计数。
     *
     * 如果当前线程不持有此锁，返回0。
     * 用于实现可重入锁的计数功能。
     *
     * @return 当前线程的持有计数，如果线程不持有锁则返回0
     */
    @Override
    public int getHoldCount() {
        return isHeldByCurrentThread() ? holdCount : 0;
    }

    /**
     * 子类必须实现的获取锁原语。
     *
     * 尝试让当前线程获取锁，不考虑重入和队列。
     * 这是模板方法模式的一部分，由具体子类实现。
     *
     * @param current 当前线程
     * @return true如果成功获取锁，false否则
     */
    protected abstract boolean tryAcquire(Thread current);

    /**
     * 子类必须实现的排队获取锁原语。
     *
     * 当快速获取失败时，将线程加入等待队列并阻塞直到获取锁。
     * 支持中断响应。
     *
     * @throws InterruptedException 如果在等待过程中被中断
     */
    protected abstract void enqueueAndAcquire() throws InterruptedException;

    /**
     * 子类必须实现的超时获取锁原语。
     *
     * 在指定时间内尝试获取锁，支持超时和中断。
     *
     * @param nanos 等待的最大时间（纳秒）
     * @return true如果在指定时间内成功获取锁，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     */
    protected abstract boolean tryAcquireWithTimeout(long nanos) throws InterruptedException;
}

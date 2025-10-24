/**
 * 自定义可重入锁接口，定义了锁的基本操作和状态查询方法。
 *
 * 该接口提供了与java.util.concurrent.locks.Lock类似的功能，但专门针对
 * 自定义实现的CLH和MCS队列锁设计。支持可重入、中断响应、超时获取等特性。
 *
 * 实现该接口的锁具有以下特点：
 * - 可重入性：同一线程可以多次获取同一个锁
 * - 公平性可选：支持公平和非公平两种模式
 * - 队列策略：支持CLH和MCS两种队列实现
 * - 等待策略：支持不同的线程等待策略
 *
 * @author 示例作者
 * @since 1.0
 * @see java.util.concurrent.locks.Lock
 */
package com.example.lock.api;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public interface MyLock {

    /**
     * 获取锁。如果锁不可用，当前线程将被阻塞直到获取锁为止。
     *
     * 该方法支持可重入，同一线程可以多次调用lock()，每次调用需要对应一次unlock()。
     * 当锁被首次获取时，持有计数设置为1。
     *
     * @throws IllegalMonitorStateException 如果在未持有锁的情况下调用unlock()
     */
    void lock();

    /**
     * 可中断地获取锁。如果锁不可用，当前线程将被阻塞，但在等待过程中可以响应中断。
     *
     * 与lock()方法类似，该方法也支持可重入。如果在等待过程中被中断，
     * 将抛出InterruptedException并清除当前线程的中断状态。
     *
     * @throws InterruptedException 如果在等待获取锁的过程中被中断
     * @throws IllegalMonitorStateException 如果在未持有锁的情况下调用unlock()
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * 尝试获取锁。如果锁立即可用，则获取锁并返回true；否则立即返回false，不阻塞线程。
     *
     * 这是一个非阻塞的获取锁方法，适用于需要避免线程阻塞的场景。
     * 同样支持可重入特性。
     *
     * @return true如果成功获取锁，false如果锁不可用
     */
    boolean tryLock();

    /**
     * 在指定时间内尝试获取锁。如果锁在给定时间内变得可用，则获取锁并返回true；
     * 否则在超时后返回false。
     *
     * 该方法允许线程在等待一定时间后放弃获取锁，适用于有时间限制的场景。
     * 支持可重入，在等待过程中可以响应中断。
     *
     * @param time 等待的最大时间
     * @param unit 时间单位
     * @return true如果在指定时间内成功获取锁，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     * @throws IllegalArgumentException 如果时间参数为负数
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁。每次调用都会减少持有计数，当持有计数降为0时，锁被完全释放。
     *
     * 只有锁的持有者才能调用此方法，否则会抛出IllegalMonitorStateException。
     * 对于可重入锁，需要确保lock()和unlock()的调用次数匹配。
     *
     * @throws IllegalMonitorStateException 如果当前线程不是锁的持有者
     */
    void unlock();

    /**
     * 创建与此锁关联的新的Condition实例。
     *
     * 返回的Condition实例支持类似Object监视器方法的功能（await、signal、signalAll），
     * 但提供了更丰富的线程协调功能。当前线程必须持有锁才能调用Condition的方法。
     *
     * @return 与此锁关联的新Condition实例
     * @throws UnsupportedOperationException 如果锁实现不支持条件变量
     */
    Condition newCondition();

    /**
     * 查询此锁是否被任何线程持有。
     *
     * 这是一个状态查询方法，用于监控锁的使用情况。
     * 注意：该方法是快照，结果可能在返回后就过时。
     *
     * @return true如果有任何线程持有此锁，false如果锁未被持有
     */
    boolean isLocked();

    /**
     * 查询当前线程是否持有此锁。
     *
     * 用于检查当前执行线程是否是锁的持有者，这在实现线程安全的代码时很有用。
     *
     * @return true如果当前线程持有此锁，false否则
     */
    boolean isHeldByCurrentThread();

    /**
     * 获取当前线程对此锁的持有计数。
     *
     * 对于可重入锁，线程可以多次获取同一个锁。此方法返回当前线程
     * 已经获取但尚未释放此锁的次数。如果当前线程不持有此锁，返回0。
     *
     * @return 当前线程的持有计数，如果线程不持有锁则返回0
     */
    int getHoldCount();
}
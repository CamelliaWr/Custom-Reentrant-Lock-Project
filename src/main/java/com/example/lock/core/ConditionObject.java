/**
 * 条件对象实现类，提供了基于自定义锁的条件变量功能。
 *
 * 该类实现了java.util.concurrent.locks.Condition接口，为AbstractQueuedLock
 * 提供了条件变量的支持。条件变量允许线程在特定条件下等待，直到被其他线程唤醒。
 *
 * 主要特性：
 * - 支持可中断和不可中断的等待
 * - 支持超时等待
 * - 支持单个唤醒(signal)和批量唤醒(signalAll)
 * - 线程安全的等待队列管理
 * - 支持锁的可重入性
 *
 * 使用示例：
 * <pre>
 * MyLock lock = LockFactory.createCLHLock();
 * Condition condition = lock.newCondition();
 *
 * lock.lock();
 * try {
 *     while (!conditionMet()) {
 *         condition.await();
 *     }
 *     // 执行业务逻辑
 * } finally {
 *     lock.unlock();
 * }
 * </pre>
 *
 * @author 示例作者
 * @since 1.0
 * @see java.util.concurrent.locks.Condition
 * @see AbstractQueuedLock
 */
package com.example.lock.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

public class ConditionObject implements Condition {

    /**
     * 关联的锁实例，条件变量必须与特定的锁绑定
     */
    private final AbstractQueuedLock lock;

    /**
     * 等待线程队列，使用FIFO顺序管理等待的线程
     */
    private final Queue<Waiter> waiters = new ArrayDeque<>();

    /**
     * 等待者内部类，封装了等待线程的信息
     */
    private static class Waiter {
        /** 等待的线程 */
        final Thread thread;
        /** 唤醒标志，true表示线程已被唤醒 */
        volatile boolean signalled = false;

        /**
         * 构造方法
         * @param t 等待的线程
         */
        Waiter(Thread t) { this.thread = t; }
    }

    /**
     * 构造方法
     * @param lock 关联的锁实例
     * @throws NullPointerException 如果lock为null
     */
    public ConditionObject(AbstractQueuedLock lock) { this.lock = lock; }

    /**
     * 使当前线程等待，直到被唤醒或中断。
     *
     * 实现步骤：
     * 1. 验证当前线程持有锁
     * 2. 保存当前的持有计数
     * 3. 完全释放锁（考虑到可重入）
     * 4. 将当前线程加入等待队列
     * 5. 阻塞等待被唤醒或中断
     * 6. 重新获取锁（恢复到原来的持有计数）
     *
     * @throws InterruptedException 如果在等待过程中被中断
     * @throws IllegalMonitorStateException 如果当前线程不持有锁
     */
    @Override
    public void await() throws InterruptedException {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        int saved = lock.getHoldCount();
        for (int i = 0; i < saved; i++) lock.unlock();

        Waiter w = new Waiter(Thread.currentThread());
        synchronized (waiters) { waiters.add(w); }

        while (!w.signalled) {
            LockSupport.park(this);
            if (Thread.interrupted()) {
                synchronized (waiters) { waiters.remove(w); }
                reacquire(saved);
                throw new InterruptedException();
            }
        }
        reacquire(saved);
    }

    /**
     * 重新获取锁，恢复到指定的持有计数。
     *
     * 使用tryLock循环尝试获取锁，然后通过多次lock()达到原来的持有计数。
     *
     * @param times 目标持有计数
     */
    private void reacquire(int times) {
        while (!lock.tryLock()) LockSupport.parkNanos(1_000_000);
        for (int i = 1; i < times; i++) lock.lock();
    }

    /**
     * 使当前线程等待，直到被唤醒（不响应中断）。
     *
     * 与await()类似，但不响应中断。如果在等待过程中被中断，
     * 中断状态会被保存，但等待会继续直到被唤醒。
     *
     * @throws IllegalMonitorStateException 如果当前线程不持有锁
     */
    @Override
    public void awaitUninterruptibly() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        int saved = lock.getHoldCount();
        for (int i = 0; i < saved; i++) lock.unlock();

        Waiter w = new Waiter(Thread.currentThread());
        synchronized (waiters) { waiters.add(w); }

        while (!w.signalled) LockSupport.park(this);
        reacquire(saved);
    }

    /**
     * 在指定纳秒内等待，直到被唤醒、中断或超时。
     *
     * 返回实际等待的纳秒数，如果超时返回0。
     *
     * @param nanosTimeout 最大等待时间（纳秒）
     * @return 剩余的等待时间（纳秒），如果超时返回0
     * @throws InterruptedException 如果在等待过程中被中断
     * @throws IllegalMonitorStateException 如果当前线程不持有锁
     */
    @Override
    public long awaitNanos(long nanosTimeout) throws InterruptedException {
        long deadline = System.nanoTime() + nanosTimeout;
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        int saved = lock.getHoldCount();
        for (int i = 0; i < saved; i++) lock.unlock();

        Waiter w = new Waiter(Thread.currentThread());
        synchronized (waiters) { waiters.add(w); }

        while (!w.signalled) {
            long rem = deadline - System.nanoTime();
            if (rem <= 0) { synchronized (waiters) { waiters.remove(w); } reacquire(saved); return 0; }
            LockSupport.parkNanos(this, Math.min(rem, 1_000_000));
            if (Thread.interrupted()) { synchronized (waiters) { waiters.remove(w); } reacquire(saved); throw new InterruptedException(); }
        }
        reacquire(saved);
        return deadline - System.nanoTime();
    }

    /**
     * 在指定时间内等待，直到被唤醒、中断或超时。
     *
     * @param time 等待时间
     * @param unit 时间单位
     * @return true如果在指定时间内被唤醒，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     */
    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        return awaitNanos(unit.toNanos(time)) > 0;
    }

    /**
     * 等待直到指定的截止日期，直到被唤醒、中断或超时。
     *
     * @param deadline 截止日期
     * @return true如果在截止日期前被唤醒，false如果超时
     * @throws InterruptedException 如果在等待过程中被中断
     */
    @Override
    public boolean awaitUntil(java.util.Date deadline) throws InterruptedException {
        long ms = deadline.getTime() - System.currentTimeMillis();
        return ms <= 0 ? false : await(ms, TimeUnit.MILLISECONDS);
    }

    /**
     * 唤醒一个等待的线程。
     *
     * 从等待队列中移除第一个线程并唤醒它。如果没有等待的线程，此方法无效果。
     *
     * @throws IllegalMonitorStateException 如果当前线程不持有锁
     */
    @Override
    public void signal() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        Waiter w;
        synchronized (waiters) { w = waiters.poll(); }
        if (w != null) { w.signalled = true; LockSupport.unpark(w.thread); }
    }

    /**
     * 唤醒所有等待的线程。
     *
     * 将等待队列中的所有线程标记为已唤醒并解除阻塞。
     *
     * @throws IllegalMonitorStateException 如果当前线程不持有锁
     */
    @Override
    public void signalAll() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        synchronized (waiters) {
            for (Waiter w : waiters) { w.signalled = true; LockSupport.unpark(w.thread); }
            waiters.clear();
        }
    }
}

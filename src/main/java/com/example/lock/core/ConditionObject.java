package com.example.lock.core;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

public class ConditionObject implements Condition {

    private final AbstractQueuedLock lock;
    private final Queue<Waiter> waiters = new ArrayDeque<>();

    private static class Waiter {
        final Thread thread;
        volatile boolean signalled = false;
        Waiter(Thread t) { this.thread = t; }
    }

    public ConditionObject(AbstractQueuedLock lock) { this.lock = lock; }

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

    private void reacquire(int times) {
        while (!lock.tryLock()) LockSupport.parkNanos(1_000_000);
        for (int i = 1; i < times; i++) lock.lock();
    }

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

    @Override
    public boolean await(long time, TimeUnit unit) throws InterruptedException {
        return awaitNanos(unit.toNanos(time)) > 0;
    }

    @Override
    public boolean awaitUntil(java.util.Date deadline) throws InterruptedException {
        long ms = deadline.getTime() - System.currentTimeMillis();
        return ms <= 0 ? false : await(ms, TimeUnit.MILLISECONDS);
    }

    @Override
    public void signal() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        Waiter w;
        synchronized (waiters) { w = waiters.poll(); }
        if (w != null) { w.signalled = true; LockSupport.unpark(w.thread); }
    }

    @Override
    public void signalAll() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalMonitorStateException("Not owner");
        synchronized (waiters) {
            for (Waiter w : waiters) { w.signalled = true; LockSupport.unpark(w.thread); }
            waiters.clear();
        }
    }
}

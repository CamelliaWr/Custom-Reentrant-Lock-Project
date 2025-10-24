package com.example.lock.core;

import com.example.lock.api.MyLock;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractQueuedLock implements MyLock {

    protected final AtomicReference<Thread> owner = new AtomicReference<>();
    protected volatile int holdCount = 0;

    protected final QueuePolicy queue;
    protected final boolean fair;
    protected final WaitStrategy waitStrategy;

    protected AbstractQueuedLock(QueuePolicy queue, boolean fair, WaitStrategy waitStrategy) {
        this.queue = queue;
        this.fair = fair;
        this.waitStrategy = waitStrategy;
        this.queue.setLock(this);
    }

    @Override
    public void lock() {
        Thread current = Thread.currentThread();
        if (current == owner.get()) {
            holdCount++;
            return;
        }
        if (!fair && owner.compareAndSet(null, current)) {
            holdCount = 1;
            return;
        }
        try {
            enqueueAndAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return; }
        if (!fair && owner.compareAndSet(null, current)) { holdCount = 1; return; }
        enqueueAndAcquire();
    }

    @Override
    public boolean tryLock() {
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return true; }
        if (owner.compareAndSet(null, current)) { holdCount = 1; return true; }
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(time);
        Thread current = Thread.currentThread();
        if (current == owner.get()) { holdCount++; return true; }
        if (owner.compareAndSet(null, current)) { holdCount = 1; return true; }
        return tryAcquireWithTimeout(nanos);
    }

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

    @Override
    public java.util.concurrent.locks.Condition newCondition() {
        return new ConditionObject(this);
    }

    @Override
    public boolean isLocked() {
        return owner.get() != null;
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return owner.get() == Thread.currentThread();
    }

    @Override
    public int getHoldCount() {
        return isHeldByCurrentThread() ? holdCount : 0;
    }

    // 子类提供的原语
    protected abstract boolean tryAcquire(Thread current);
    protected abstract void enqueueAndAcquire() throws InterruptedException;
    protected abstract boolean tryAcquireWithTimeout(long nanos) throws InterruptedException;
}

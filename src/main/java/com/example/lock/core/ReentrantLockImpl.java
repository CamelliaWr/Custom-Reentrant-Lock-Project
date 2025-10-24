package com.example.lock.core;

import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

public class ReentrantLockImpl extends AbstractQueuedLock {

    public ReentrantLockImpl(QueuePolicy queue, boolean fair, WaitStrategy waitStrategy) {
        super(queue, fair, waitStrategy);
    }

    @Override
    protected boolean tryAcquire(Thread current) {
        if (current == owner.get()) { holdCount++; return true; }
        if (owner.compareAndSet(null, current)) { holdCount = 1; return true; }
        return false;
    }

    @Override
    protected void enqueueAndAcquire() throws InterruptedException {
        queue.enqueueAndAcquire(this::tryAcquire, waitStrategy);
    }

    @Override
    protected boolean tryAcquireWithTimeout(long nanos) throws InterruptedException {
        return queue.tryAcquireWithTimeout(this::tryAcquire, nanos, waitStrategy);
    }
}

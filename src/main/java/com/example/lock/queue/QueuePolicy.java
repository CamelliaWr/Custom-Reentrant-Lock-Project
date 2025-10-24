package com.example.lock.queue;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.strategy.WaitStrategy;

public interface QueuePolicy {
    void setLock(Object lock);
    void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException;
    boolean tryAcquireWithTimeout(TryAcquireFunction tryAcquire, long nanos, WaitStrategy waitStrategy) throws InterruptedException;
    void unparkSuccessor();
}

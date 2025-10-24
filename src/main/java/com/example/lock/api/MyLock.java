package com.example.lock.api;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

public interface MyLock {
    void lock();
    void lockInterruptibly() throws InterruptedException;
    boolean tryLock();
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;
    void unlock();
    Condition newCondition();
    boolean isLocked();
    boolean isHeldByCurrentThread();
    int getHoldCount();
}

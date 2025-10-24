package com.example.lock.api;

import com.example.lock.core.ReentrantLockImpl;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.queue.clh.CLHQueue;
import com.example.lock.queue.mcs.MCSQueue;
import com.example.lock.strategy.BusySpinStrategy;
import com.example.lock.strategy.SpinThenParkStrategy;
import com.example.lock.strategy.WaitStrategy;

import java.util.Objects;

public final class LockFactory {

    public enum QueueType { CLH, MCS }
    public enum WaitPolicy { SPIN_THEN_PARK, BUSY_SPIN }

    private LockFactory() {}

    public static MyLock createReentrantLock(boolean fair, QueueType qtype, WaitPolicy waitPolicy) {
        Objects.requireNonNull(qtype);
        Objects.requireNonNull(waitPolicy);

        QueuePolicy queue;
        switch (qtype) {
            case MCS:
                queue = new MCSQueue();
                break;
            case CLH:
            default:
                queue = new CLHQueue();
                break;
        }

        WaitStrategy waitStrategy;
        switch (waitPolicy) {
            case BUSY_SPIN:
                waitStrategy = new BusySpinStrategy();
                break;
            case SPIN_THEN_PARK:
            default:
                waitStrategy = new SpinThenParkStrategy();
                break;
        }

        return new ReentrantLockImpl(queue, fair, waitStrategy);
    }

    public static MyLock createCLHLock() {
        return new ReentrantLockImpl(new CLHQueue(), true, new SpinThenParkStrategy());
    }

    public static MyLock createMCSLock() {
        return new ReentrantLockImpl(new MCSQueue(), true, new SpinThenParkStrategy());
    }

    public static MyLock createCustomReentrantLock() {
        return new ReentrantLockImpl(new CLHQueue(), false, new BusySpinStrategy());
    }
}

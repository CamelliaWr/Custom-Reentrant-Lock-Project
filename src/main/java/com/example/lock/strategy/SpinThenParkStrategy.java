package com.example.lock.strategy;

import java.util.concurrent.locks.LockSupport;

public class SpinThenParkStrategy implements WaitStrategy {
    private final int spins;

    public SpinThenParkStrategy() { this(100); }
    public SpinThenParkStrategy(int spins) { this.spins = spins; }

    @Override
    public void await() throws InterruptedException {
        for (int i = 0; i < spins; i++) Thread.onSpinWait();
        LockSupport.parkNanos(1_000);
        if (Thread.interrupted()) throw new InterruptedException();
    }
}

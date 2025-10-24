package com.example.lock.strategy;

public class BusySpinStrategy implements WaitStrategy {
    private final int maxSpins;

    public BusySpinStrategy() { this(10_000); }
    public BusySpinStrategy(int maxSpins) { this.maxSpins = maxSpins; }

    @Override
    public void await() throws InterruptedException {
        int spins = 0;
        while (spins++ < maxSpins) Thread.onSpinWait();
        Thread.yield();
        if (Thread.interrupted()) throw new InterruptedException();
    }
}

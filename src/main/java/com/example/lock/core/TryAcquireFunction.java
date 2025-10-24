package com.example.lock.core;

@FunctionalInterface
public interface TryAcquireFunction {
    boolean tryAcquire(Thread current);
}

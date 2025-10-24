package com.example.lock.strategy;

/**
 * 抽象等待策略：队列实现会在自旋/park 时调用它（可以用于统一配置）。
 */
public interface WaitStrategy {
    void await() throws InterruptedException;
}

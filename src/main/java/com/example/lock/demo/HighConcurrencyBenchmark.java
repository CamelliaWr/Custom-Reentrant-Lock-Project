package com.example.lock.demo;

import com.example.lock.api.MyLock;
import com.example.lock.api.LockFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class HighConcurrencyBenchmark {

    private static final int THREADS = 50;     // 高并发线程数
    private static final int ITERATIONS = 20;  // 每线程重复获取锁次数

    public static void main(String[] args) throws InterruptedException {
        System.out.println("高并发基准测试开始...");

        benchmarkLock("CLH锁", LockFactory.createCLHLock());
        benchmarkLock("MCS锁", LockFactory.createMCSLock());
        benchmarkLock("自定义可重入锁", LockFactory.createCustomReentrantLock());

        System.out.println("基准测试完成。");
    }

    private static void benchmarkLock(String name, MyLock lock) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREADS);
        AtomicLong totalWaitTime = new AtomicLong(0);

        long start = System.nanoTime();

        for (int t = 0; t < THREADS; t++) {
            final int tid = t + 1;
            new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    long acquireStart = System.nanoTime();
                    lock.lock();
                    long acquireEnd = System.nanoTime();
                    totalWaitTime.addAndGet(acquireEnd - acquireStart);

                    int hold = lock.getHoldCount();
                    System.out.printf("线程%d 第%d次迭代获取锁，持有计数=%d%n", tid, i + 1, hold);

                    // 模拟工作
                    try { Thread.sleep(ThreadLocalRandom.current().nextInt(5, 15)); } catch (InterruptedException e) { e.printStackTrace(); }

                    // 测试重入
                    lock.lock();
                    System.out.printf("线程%d 第%d次迭代重入锁，持有计数=%d%n", tid, i + 1, lock.getHoldCount());
                    lock.unlock();

                    lock.unlock();
                    System.out.printf("线程%d 第%d次迭代释放锁，持有计数=%d%n", tid, i + 1, lock.getHoldCount());
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        long totalTime = System.nanoTime() - start;

        System.out.printf("[%s] 线程数=%d, 迭代次数=%d, 总时间=%.2f 毫秒, 平均等待=%.2f 微秒%n%n",
                name, THREADS, ITERATIONS, totalTime / 1_000_000.0, totalWaitTime.get() / (THREADS * ITERATIONS) / 1_000.0);
    }
}
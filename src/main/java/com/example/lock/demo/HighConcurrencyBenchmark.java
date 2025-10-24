/**
 * 高并发基准测试类，用于测试和比较不同锁实现的性能表现。
 *
 * 该测试类模拟了真实的高并发场景，通过多线程竞争锁来评估：
 * - 锁获取延迟（平均等待时间）
 * - 锁吞吐量（总耗时）
 * - 可重入性表现
 * - 不同锁实现的性能对比
 *
 * 测试设计特点：
 * - 使用固定线程池模拟并发压力
 * - 每个线程重复获取锁多次，模拟真实业务场景
 * - 在锁内模拟随机耗时操作，增加测试真实性
 * - 测试锁的可重入特性
 * - 精确测量锁获取时间
 *
 * 测试指标：
 * - 总耗时：整个测试过程的耗时
 * - 平均等待时间：每次锁获取的平均等待时间
 * - 锁获取成功率：通过完成测试间接反映
 *
 * @author 示例作者
 * @since 1.0
 * @see MyLock
 * @see LockFactory
 */
package com.example.lock.demo;

import com.example.lock.api.MyLock;
import com.example.lock.api.LockFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class HighConcurrencyBenchmark {

    /**
     * 并发线程数，模拟高并发场景下的线程竞争。
     * 50个线程可以很好地模拟中等规模的高并发压力。
     */
    private static final int THREADS = 50;     // 高并发线程数

    /**
     * 每个线程重复获取锁的次数。
     * 20次迭代可以充分测试锁的稳定性和性能表现。
     */
    private static final int ITERATIONS = 20;  // 每线程重复获取锁次数

    /**
     * 主方法，执行完整的基准测试流程。
     *
     * 测试流程：
     * 1. 输出测试开始信息
     * 2. 依次测试不同类型的锁实现
     * 3. 输出测试结果和对比数据
     * 4. 输出测试完成信息
     *
     * 测试的锁类型：
     * - CLH锁：基于隐式链表的自旋锁
     * - MCS锁：基于显式链表的自旋锁
     * - 自定义可重入锁：非公平锁，使用忙等待策略
     *
     * @param args 命令行参数（未使用）
     * @throws InterruptedException 如果测试过程中被中断
     */
    public static void main(String[] args) throws InterruptedException {
        System.out.println("高并发基准测试开始...");

        // 依次测试不同类型的锁实现
        benchmarkLock("CLH锁", LockFactory.createCLHLock());
        benchmarkLock("MCS锁", LockFactory.createMCSLock());
        benchmarkLock("自定义可重入锁", LockFactory.createCustomReentrantLock());

        System.out.println("基准测试完成。");
    }

    /**
     * 对指定锁进行并发性能基准测试。
     *
     * 测试方法：
     * 1. 创建指定数量的工作线程
     * 2. 每个线程重复执行锁获取-工作-释放的循环
     * 3. 精确测量每次锁获取的等待时间
     * 4. 测试锁的可重入特性
     * 5. 统计并输出性能指标
     *
     * 测试内容：
     * - 锁获取延迟测量
     * - 锁内工作模拟（随机耗时5-15ms）
     * - 锁重入测试（每个循环中重入一次）
     * - 多线程并发竞争测试
     *
     * @param name 锁的名称，用于输出标识
     * @param lock 要测试的锁实例
     * @throws InterruptedException 如果测试过程中被中断
     *
     * @implNote 使用CountDownLatch确保所有线程同步完成
     * @implNote 使用AtomicLong进行线程安全的耗时统计
     * @implNote 使用ThreadLocalRandom生成随机工作时间
     */
    private static void benchmarkLock(String name, MyLock lock) throws InterruptedException {
        // 用于同步所有工作线程的完成
        CountDownLatch latch = new CountDownLatch(THREADS);

        // 线程安全的总等待时间计数器
        AtomicLong totalWaitTime = new AtomicLong(0);

        // 记录测试开始时间
        long start = System.nanoTime();

        // 创建并启动所有工作线程
        for (int t = 0; t < THREADS; t++) {
            final int tid = t + 1;  // 线程ID（1-based）
            new Thread(() -> {
                // 每个线程执行指定次数的锁操作
                for (int i = 0; i < ITERATIONS; i++) {
                    // 测量锁获取时间
                    long acquireStart = System.nanoTime();
                    lock.lock();
                    long acquireEnd = System.nanoTime();
                    // 累加等待时间
                    totalWaitTime.addAndGet(acquireEnd - acquireStart);

                    // 获取当前持有计数，用于监控
                    int hold = lock.getHoldCount();
                    System.out.printf("线程%d 第%d次迭代获取锁，持有计数=%d%n", tid, i + 1, hold);

                    // 模拟锁内的业务工作，随机耗时5-15毫秒
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(5, 15));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // 测试锁的可重入特性
                    lock.lock();
                    System.out.printf("线程%d 第%d次迭代重入锁，持有计数=%d%n", tid, i + 1, lock.getHoldCount());
                    lock.unlock();

                    // 释放外层锁
                    lock.unlock();
                    System.out.printf("线程%d 第%d次迭代释放锁，持有计数=%d%n", tid, i + 1, lock.getHoldCount());
                }
                // 通知主线程该工作线程已完成
                latch.countDown();
            }).start();
        }

        // 等待所有工作线程完成
        latch.await();
        // 计算总耗时
        long totalTime = System.nanoTime() - start;

        // 输出测试结果统计信息
        System.out.printf("[%s] 线程数=%d, 迭代次数=%d, 总时间=%.2f 毫秒, 平均等待=%.2f 微秒%n%n",
                name, THREADS, ITERATIONS,
                totalTime / 1_000_000.0,  // 纳秒转毫秒
                totalWaitTime.get() / (THREADS * ITERATIONS) / 1_000.0);  // 平均等待时间（微秒）
    }
}
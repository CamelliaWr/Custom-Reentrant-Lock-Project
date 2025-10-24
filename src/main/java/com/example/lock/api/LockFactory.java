package com.example.lock.api;

import com.example.lock.core.ReentrantLockImpl;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.queue.clh.CLHQueue;
import com.example.lock.queue.mcs.MCSQueue;
import com.example.lock.strategy.BusySpinStrategy;
import com.example.lock.strategy.SpinThenParkStrategy;
import com.example.lock.strategy.WaitStrategy;

import java.util.Objects;

/**
 * 可重入锁工厂类，用于创建各种类型的自定义可重入锁实现。
 *
 * 该工厂提供了多种锁创建方法，支持不同的队列策略和等待策略组合：
 * - CLH (Craig, Landin, and Hagersten) 队列：基于隐式链表的自旋锁
 * - MCS 队列：基于显式链表的自旋锁
 * - 自旋后阻塞策略：先自旋一段时间，然后使用LockSupport.park
 * - 忙等待策略：持续自旋直到获取锁
 *
 * 所有创建的锁都实现了MyLock接口，支持可重入、公平性配置、条件变量等特性。
 *
 * @author 示例作者
 * @since 1.0
 */
public final class LockFactory {

    /**
     * 队列类型枚举，定义了支持的两种队列实现策略
     */
    public enum QueueType {
        /** CLH队列：基于隐式链表的自旋锁，每个线程在本地内存中自旋 */
        CLH,
        /** MCS队列：基于显式链表的自旋锁，每个线程在前驱节点的锁变量上自旋 */
        MCS
    }

    /**
     * 等待策略枚举，定义了线程在获取锁失败时的等待方式
     */
    public enum WaitPolicy {
        /** 自旋后阻塞策略：先自旋一段时间，然后使用LockSupport.park阻塞 */
        SPIN_THEN_PARK,
        /** 忙等待策略：持续自旋，CPU消耗较高但延迟较低 */
        BUSY_SPIN
    }

    /**
     * 私有构造函数，防止实例化
     * 这是一个工具类，所有方法都是静态的
     */
    private LockFactory() {}

    /**
     * 创建自定义可重入锁，允许指定公平性、队列类型和等待策略
     *
     * @param fair 是否公平锁。true表示公平锁，按照请求顺序获取锁；false表示非公平锁，允许插队
     * @param qtype 队列类型，可选择CLH或MCS队列
     * @param waitPolicy 等待策略，可选择自旋后阻塞或忙等待
     * @return 配置完成的可重入锁实例
     * @throws NullPointerException 如果qtype或waitPolicy为null
     *
     * @example
     * MyLock lock = LockFactory.createReentrantLock(true, QueueType.CLH, WaitPolicy.SPIN_THEN_PARK);
     */
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

    /**
     * 创建标准的CLH公平锁，使用自旋后阻塞等待策略
     *
     * 这是一个便捷方法，相当于：
     * createReentrantLock(true, QueueType.CLH, WaitPolicy.SPIN_THEN_PARK)
     *
     * @return CLH公平锁实例
     */
    public static MyLock createCLHLock() {
        return new ReentrantLockImpl(new CLHQueue(), true, new SpinThenParkStrategy());
    }

    /**
     * 创建标准的MCS公平锁，使用自旋后阻塞等待策略
     *
     * 这是一个便捷方法，相当于：
     * createReentrantLock(true, QueueType.MCS, WaitPolicy.SPIN_THEN_PARK)
     *
     * @return MCS公平锁实例
     */
    public static MyLock createMCSLock() {
        return new ReentrantLockImpl(new MCSQueue(), true, new SpinThenParkStrategy());
    }

    /**
     * 创建自定义的非公平锁，使用CLH队列和忙等待策略
     *
     * 这是一个便捷方法，相当于：
     * createReentrantLock(false, QueueType.CLH, WaitPolicy.BUSY_SPIN)
     *
     * 适用于低延迟要求的场景，但CPU使用率较高
     *
     * @return 自定义非公平锁实例
     */
    public static MyLock createCustomReentrantLock() {
        return new ReentrantLockImpl(new CLHQueue(), false, new BusySpinStrategy());
    }
}
/**
 * CLH锁节点类，用于实现基于链表的自旋锁。
 *
 * CLH锁（Craig, Landin, and Hagersten locks）是一种基于链表的高性能自旋锁实现，
 * 通过维护一个隐式的等待线程队列来避免缓存一致性流量，提高多核处理器上的性能。
 *
 * 设计特点：
 * - 每个线程拥有独立的节点，避免伪共享
 * - 使用volatile保证内存可见性
 * - 基于前驱节点的状态进行自旋等待
 * - 支持公平锁获取，避免线程饥饿
 *
 * 节点状态：
 * - locked: true 表示线程正在等待或持有锁
 * - locked: false 表示线程已释放锁，后继节点可以获取锁
 *
 * @author 示例作者
 * @since 1.0
 * @see CLHLock
 */
package com.example.lock.queue.clh;

/**
 * CLH锁节点实现类。
 *
 * 该类封装了CLH锁算法中的节点信息，包括：
 * - 前驱节点引用（用于自旋等待）
 * - 后继节点引用（用于通知机制）
 * - 锁状态标识（当前线程是否持有锁）
 * - 关联的线程对象（用于调试和监控）
 */
public class CLHNode {

    /**
     * 前驱节点引用。
     *
     * 用于构建等待线程的隐式链表，当前线程通过监视前驱节点的locked状态
     * 来决定何时可以获取锁。volatile保证多线程间的可见性。
     */
    volatile CLHNode prev;

    /**
     * 后继节点引用。
     *
     * 用于支持更复杂的通知机制，在某些CLH变体实现中可用于优化
     * 锁释放时的通知过程。volatile保证多线程间的可见性。
     */
    volatile CLHNode next;

    /**
     * 锁状态标识。
     *
     * true: 表示当前线程正在等待锁或持有锁
     * false: 表示当前线程已释放锁，后继节点可以停止自旋
     *
     * 初始值为true，因为节点创建时通常表示线程即将进入等待状态。
     * volatile保证状态变更的内存可见性。
     */
    volatile boolean locked = true;

    /**
     * 关联的线程对象。
     *
     * final修饰确保线程引用不可变，用于：
     * - 调试时识别节点对应的线程
     * - 监控和日志记录
     * - 死锁检测和分析
     */
    final Thread thread;

    /**
     * 默认构造方法。
     *
     * 创建一个新的CLH节点，自动关联当前执行线程。
     * 初始化时设置locked为true，表示线程即将进入锁等待状态。
     *
     * @implNote 使用Thread.currentThread()获取当前线程，确保节点与线程的正确关联
     */
    public CLHNode() {
        this.thread = Thread.currentThread();
    }
}
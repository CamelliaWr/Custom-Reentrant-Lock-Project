/**
 * MCS锁节点类，用于实现基于显式链表的自旋锁。
 *
 * MCS（Mellor-Crummey and Scott）锁是一种基于链表的高性能自旋锁实现，
 * 通过维护显式的等待线程队列来避免缓存一致性流量，特别适合NUMA架构的多核处理器。
 *
 * 设计特点：
 * - 每个线程拥有独立的节点，避免伪共享
 * - 使用volatile保证内存可见性
 * - 基于本地变量的自旋等待，减少远程内存访问
 * - 支持公平锁获取，避免线程饥饿
 * - 显式链表结构，便于实现复杂的通知机制
 *
 * 节点状态：
 * - locked: true 表示线程正在等待锁
 * - locked: false 表示线程已获取锁或可以获取锁
 * - next: 指向队列中的下一个等待节点
 *
 * 与CLH节点的区别：
 * - MCS使用显式的后继指针（next）
 * - MCS节点在本地变量上自旋，而CLH在前驱节点上自旋
 * - MCS更适合NUMA架构，CLH更适合UMA架构
 *
 * @author 示例作者
 * @since 1.0
 * @see MCSQueue
 * @see CLHNode
 */
package com.example.lock.queue.mcs;

/**
 * MCS锁节点实现类。
 *
 * 该类封装了MCS算法中的节点信息，包括：
 * - 后继节点引用（用于构建显式等待队列）
 * - 锁状态标识（当前线程是否持有锁）
 * - 关联的线程对象（用于调试和监控）
 *
 * MCS算法特点：
 * - 每个线程在本地节点上自旋等待
 * - 锁释放时直接通知后继节点
 * - 避免远程内存访问，提高NUMA架构性能
 */
public class MCSNode {

    /**
     * 后继节点引用。
     *
     * 用于构建显式的等待线程队列，当前线程释放锁时
     * 可以通过next指针直接通知后继节点。
     * volatile保证多线程间的可见性。
     *
     * MCS使用显式链表，而CLH使用隐式链表，
     * 这使得MCS在锁释放时可以直接唤醒特定线程。
     */
    volatile MCSNode next;

    /**
     * 锁状态标识。
     *
     * true: 表示当前线程正在等待锁
     * false: 表示当前线程已获取锁或可以获取锁
     *
     * 初始值为true，因为节点创建时通常表示线程即将进入等待状态。
     * volatile保证状态变更的内存可见性。
     *
     * 与CLH不同，MCS节点在本地locked变量上自旋，
     * 减少了远程内存访问的开销。
     */
    volatile boolean locked = true;

    /**
     * 关联的线程对象。
     *
     * final修饰确保线程引用不可变，用于：
     * - 调试时识别节点对应的线程
     * - 监控和日志记录
     * - 死锁检测和分析
     * - 锁释放时的线程唤醒（通过LockSupport）
     */
    final Thread thread;

    /**
     * 默认构造方法。
     *
     * 创建一个新的MCS节点，自动关联当前执行线程。
     * 初始化时设置locked为true，表示线程即将进入锁等待状态。
     *
     * @implNote 使用Thread.currentThread()获取当前线程，确保节点与线程的正确关联
     * @implNote 每个线程拥有独立的MCS节点，避免节点共享带来的性能问题
     */
    public MCSNode() {
        this.thread = Thread.currentThread();
    }
}

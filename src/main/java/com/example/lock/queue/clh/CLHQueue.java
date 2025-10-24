package com.example.lock.queue.clh;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class CLHQueue implements QueuePolicy {

    private final AtomicReference<CLHNode> tail = new AtomicReference<>();
    private final ThreadLocal<CLHNode> myNode = ThreadLocal.withInitial(CLHNode::new);

    @Override
    public void setLock(Object lock) { }

    @Override
    public void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException {
        CLHNode node = myNode.get();
        CLHNode pred = tail.getAndSet(node);

        if (pred != null) {
            node.prev = pred;
            pred.next = node;
            while (pred.locked) { waitStrategy.await(); }
        }

        while (!tryAcquire.tryAcquire(Thread.currentThread())) { Thread.onSpinWait(); }
        node.locked = false;
    }

    @Override
    public boolean tryAcquireWithTimeout(TryAcquireFunction tryAcquire, long nanos, WaitStrategy waitStrategy) throws InterruptedException {
        long deadline = System.nanoTime() + nanos;
        CLHNode node = myNode.get();
        CLHNode pred = tail.getAndSet(node);

        if (pred != null) {
            node.prev = pred;
            pred.next = node;
            while (pred.locked) {
                if (System.nanoTime() > deadline) { cancelNode(node); return false; }
                waitStrategy.await();
            }
        }

        while (!tryAcquire.tryAcquire(Thread.currentThread())) {
            if (System.nanoTime() > deadline) { cancelNode(node); return false; }
            Thread.onSpinWait();
        }
        node.locked = false;
        return true;
    }

    @Override
    public void unparkSuccessor() {
        CLHNode t = tail.get();
        if (t == null) return;

        CLHNode cur = t;
        while (cur.prev != null) cur = cur.prev;
        CLHNode succ = cur.next;

        if (succ != null) LockSupport.unpark(succ.thread);
    }

    private void cancelNode(CLHNode node) {
        tail.compareAndSet(node, node.prev);
        if (node.prev != null) node.prev.next = node.next;
        node.prev = null;
        node.next = null;
    }
}

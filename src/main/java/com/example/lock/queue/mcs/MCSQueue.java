package com.example.lock.queue.mcs;

import com.example.lock.core.TryAcquireFunction;
import com.example.lock.queue.QueuePolicy;
import com.example.lock.strategy.WaitStrategy;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class MCSQueue implements QueuePolicy {

    private final AtomicReference<MCSNode> tail = new AtomicReference<>();
    private final ThreadLocal<MCSNode> myNode = ThreadLocal.withInitial(MCSNode::new);

    @Override
    public void setLock(Object lock) { }

    @Override
    public void enqueueAndAcquire(TryAcquireFunction tryAcquire, WaitStrategy waitStrategy) throws InterruptedException {
        MCSNode node = myNode.get();
        MCSNode pred = tail.getAndSet(node);

        if (pred != null) {
            pred.next = node;
            while (node.locked) waitStrategy.await();
        }

        while (!tryAcquire.tryAcquire(Thread.currentThread())) Thread.onSpinWait();
        node.locked = false;
    }

    @Override
    public boolean tryAcquireWithTimeout(TryAcquireFunction tryAcquire, long nanos, WaitStrategy waitStrategy) throws InterruptedException {
        long deadline = System.nanoTime() + nanos;
        MCSNode node = myNode.get();
        MCSNode pred = tail.getAndSet(node);

        if (pred != null) {
            pred.next = node;
            while (node.locked) {
                if (System.nanoTime() > deadline) { cancel(node); return false; }
                waitStrategy.await();
            }
        }

        while (!tryAcquire.tryAcquire(Thread.currentThread())) {
            if (System.nanoTime() > deadline) { cancel(node); return false; }
            Thread.onSpinWait();
        }
        node.locked = false;
        return true;
    }

    @Override
    public void unparkSuccessor() {
        MCSNode t = tail.get();
        if (t != null && t.thread != null) LockSupport.unpark(t.thread);
    }

    private void cancel(MCSNode node) {
        if (tail.get() == node) tail.compareAndSet(node, null);
        node.next = null;
    }
}

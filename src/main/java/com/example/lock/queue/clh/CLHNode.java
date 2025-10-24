package com.example.lock.queue.clh;

public class CLHNode {
    volatile CLHNode prev;
    volatile CLHNode next;
    volatile boolean locked = true;
    final Thread thread;

    public CLHNode() { this.thread = Thread.currentThread(); }
}

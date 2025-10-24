package com.example.lock.queue.mcs;

public class MCSNode {
    volatile MCSNode next;
    volatile boolean locked = true;
    final Thread thread;

    public MCSNode() { this.thread = Thread.currentThread(); }
}

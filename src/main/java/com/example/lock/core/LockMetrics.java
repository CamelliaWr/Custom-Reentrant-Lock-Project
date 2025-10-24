package com.example.lock.core;

/**
 * 简单度量占位（可扩展）。当前为演示，未集成到所有路径。
 */
public class LockMetrics {
    private long acquisitions = 0;
    private long contended = 0;

    public synchronized void incAcq() { acquisitions++; }
    public synchronized void incContended() { contended++; }

    public synchronized long getAcquisitions() { return acquisitions; }
    public synchronized long getContended() { return contended; }
}

# Custom Reentrant Lock Project

## 项目概述

本项目实现了一个自定义 **可重入锁（ReentrantLock）**，支持 **CLH / MCS** 两种队列策略和不同等待策略（BusySpin / SpinThenPark）。
它不仅支持标准锁功能，还支持 **Condition、重入、可中断/超时锁**，并提供高并发 Benchmark 测试。

本锁适合作为教学、研究或轻量生产演示使用，帮助理解 Java 锁的内部实现原理。

---

## 项目结构

```
lock-project/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/lock/
│   │   │       ├── api/
│   │   │       │   ├── LockFactory.java
│   │   │       │   └── MyLock.java
│   │   │       ├── core/
│   │   │       │   ├── AbstractQueuedLock.java
│   │   │       │   ├── ConditionObject.java
│   │   │       │   ├── LockMetrics.java
│   │   │       │   ├── ReentrantLockImpl.java
│   │   │       │   └── TryAcquireFunction.java
│   │   │       ├── demo/
│   │   │       │   └── HighConcurrencyBenchmark.java
│   │   │       ├── queue/
│   │   │       │   ├── QueuePolicy.java
│   │   │       │   ├── clh/
│   │   │       │   │   ├── CLHNode.java
│   │   │       │   │   └── CLHQueue.java
│   │   │       │   └── mcs/
│   │   │       │       ├── MCSNode.java
│   │   │       │       └── MCSQueue.java
│   │   │       └── strategy/
│   │   │           ├── BusySpinStrategy.java
│   │   │           ├── SpinThenParkStrategy.java
│   │   │           └── WaitStrategy.java
└── README.md
```

---

## 功能特性

* **可重入锁**

  * 支持 `lock()`, `unlock()`, `tryLock()`, `lockInterruptibly()`, `tryLock(timeout)`
  * 自动管理 `holdCount`

* **Condition 支持**

  * `newCondition()`, `await()`, `signal()`, `signalAll()`
  * 支持重入计数恢复、超时和中断处理

* **队列策略**

  * **CLHQueue**：基于 CLH 队列，自旋等待前驱释放
  * **MCSQueue**：基于 MCS 队列，每线程自旋自己的节点

* **等待策略**

  * **BusySpinStrategy**：纯自旋等待
  * **SpinThenParkStrategy**：先自旋，短时间 park，减少 CPU 消耗

* **Benchmark**

  * `BenchmarkWithReentrancy.java`：可重入验证示例
  * `HighConcurrencyBenchmark.java`：50~100 线程高并发性能测试
  * 输出 holdCount、获取锁时间、总耗时

---

## 使用示例

### 创建锁

```java
import com.example.lock.api.LockFactory;
import com.example.lock.api.MyLock;

public class DemoExample {
    public static void main(String[] args) throws InterruptedException {
        MyLock lock = LockFactory.createCLHLock(); // 或 createMCSLock / createCustomReentrantLock

        lock.lock();
        try {
            // critical section
            System.out.println("Lock acquired!");
        } finally {
            lock.unlock();
        }
    }
}
```

### Condition 使用

```java
var lock = LockFactory.createCLHLock();
var condition = lock.newCondition();

lock.lock();
try {
    condition.await(); // 等待信号
    condition.signal(); // 唤醒其他线程
} finally {
    lock.unlock();
}
```


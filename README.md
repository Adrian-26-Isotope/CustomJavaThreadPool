# CustomThreadPool

A flexible and efficient custom thread pool implementation in Java that provides variable thread management with configurable minimum and maximum thread counts, idle timeouts, and different task polling strategies.

## Features

- **Variable Thread Pool Size**: Configure minimum and maximum number of threads
- **Idle Thread Management**: Automatic thread termination after configurable idle time
- **Core vs Non-Core Workers**: Distinction between persistent core threads and scalable non-core threads
- **Custom Thread Factory Support**: Use your own thread factory or default to virtual threads
- **Multiple Polling Strategies**: Different task polling behaviors for various thread pool states
- **Task Completion Tracking**: Monitor completed task counts across all workers
- **Standard ExecutorService Interface**: Implements `AbstractExecutorService` for compatibility

## Quick Start

### Basic Usage

```java
import adrian.os.java.threadpool.CustomThreadPool;

// Create a thread pool with default settings
CustomThreadPool threadPool = CustomThreadPool.builder().build();

// Submit a task
threadPool.submit(() -> {
    System.out.println("Task executed by: " + Thread.currentThread().getName());
});

// Shutdown when done
threadPool.shutdown();
```

### Advanced Usage

```java
import java.time.Duration;
import java.util.concurrent.ThreadFactory;

CustomThreadPool threadPool = CustomThreadPool.builder()
    .setMinThreads(2)                          // Minimum 2 core threads
    .setMaxThreads(10)                         // Maximum 10 threads
    .setIdleTime(Duration.ofSeconds(30))       // Idle timeout of 30 seconds
    .setThreadFactory(Thread.ofPlatform().factory()) // Use platform threads
    .build();

// Submit multiple tasks
for (int i = 0; i < 100; i++) {
    final int taskId = i;
    threadPool.submit(() -> {
        System.out.println("Processing task " + taskId);
        // Your task logic here
    });
}

// Monitor progress
System.out.println("Completed tasks: " + threadPool.getCompletedTasksCount());
System.out.println("Active workers: " + threadPool.getWorkers().size());

threadPool.shutdown();
```

## Configuration Options

| Parameter | Default Value | Description |
|-----------|---------------|-------------|
| `minThreads` | `0` | Minimum number of core threads that persist even when idle |
| `maxThreads` | `Integer.MAX_VALUE` | Maximum number of threads that can be created |
| `idleTime` | `10 seconds` | Time after which idle non-core threads are terminated |
| `threadFactory` | `Thread.ofVirtual().factory()` | Factory for creating new threads |

## Architecture

### Core Components

- **`CustomThreadPool`**: Main thread pool implementation extending `AbstractExecutorService`
- **`Worker`**: Individual worker threads that execute tasks
- **`ITaskPollingStrategy`**: Interface defining task polling behavior
- **Polling Strategies**:
  - `RunningTaskPollingStrategy`: For active thread pools
  - `ShutdownTaskPollingStrategy`: For graceful shutdown
  - `TerminatedTaskPollingStrategy`: For terminated pools

### Thread Management

The thread pool distinguishes between two types of workers:

1. **Core Workers**: Created up to `minThreads` count, persist even when idle
2. **Non-Core Workers**: Created on-demand up to `maxThreads`, terminated after idle timeout

### Thread States and Lifecycle

1. **NOT_RUNNING**: Initial state and state after termination
2. **RUNNING**: Active state processing tasks
3. **SHUTDOWN**: Graceful shutdown in progress

The thread pool automatically starts upon construction and can be manually controlled using `start()`, `shutdown()`, and `shutdownNow()` methods.

## Examples

### CPU-Intensive Tasks

```java
CustomThreadPool cpuPool = CustomThreadPool.builder()
    .setMinThreads(Runtime.getRuntime().availableProcessors())
    .setMaxThreads(Runtime.getRuntime().availableProcessors())
    .setThreadFactory(Thread.ofPlatform().factory())
    .build();

// Submit CPU-bound tasks
for (int i = 0; i < 1000; i++) {
    cpuPool.submit(() -> {
        // CPU-intensive computation
        double result = Math.sqrt(Math.random() * 1000000);
    });
}
```

### I/O-Bound Tasks with Virtual Threads

```java
CustomThreadPool ioPool = CustomThreadPool.builder()
    .setMinThreads(0)
    .setMaxThreads(1000)
    .setIdleTime(Duration.ofSeconds(5))
    .setThreadFactory(Thread.ofVirtual().factory()) // Default
    .build();

// Submit I/O-bound tasks
for (int i = 0; i < 10000; i++) {
    ioPool.submit(() -> {
        try {
            // Simulate I/O operation
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
}
```

### More Examples

See [CustomThreadPoolTest.java](src-test/adrian/os/java/threadpool/CustomThreadPoolTest.java) for more examples.

## Requirements

- Java 24+
- JUnit 5

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Author

Adrian-26-Isotope

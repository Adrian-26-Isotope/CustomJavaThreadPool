package adrian.os.java.threadpool;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class CustomThreadPoolTest {

    @Test
    void testInitialTreads() {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(5).build();
        assertEquals(5, customThreadPool.getWorkers().size(), "Initial thread count should be 5");
        customThreadPool.shutdown();

        customThreadPool = CustomThreadPool.builder().setMinThreads(0).build();
        assertEquals(0, customThreadPool.getWorkers().size(), "Initial thread count should be 0");

        customThreadPool.shutdownNow();
    }

    @Test
    void testMaxTreads() {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMaxThreads(2).build();
        assertEquals(0, customThreadPool.getWorkers().size(), "Initial thread count should be 0");
        for (int i = 1; i <= 2; i++) {
            customThreadPool.submit(createRunnable(5));
            assertEquals(i, customThreadPool.getWorkers().size());
        }
        customThreadPool.submit(createRunnable(5));
        assertEquals(2, customThreadPool.getWorkers().size(), " Max thread count must not exceed 2");

        customThreadPool.shutdownNow();
    }

    @Test
    void testIdleTreads() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(2)
                .setIdleTime(Duration.ofSeconds(1)).setName("IDLE").build();
        assertEquals(2, customThreadPool.getWorkers().size());
        Thread.sleep(2000); // Wait for idle time to expire
        assertEquals(2, customThreadPool.getWorkers().size(), "threads must not deceed minimum thread count");
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        Thread.sleep(100); // wait for all tasks to be picked up
        assertEquals(10, customThreadPool.getWorkers().size());
        Thread.sleep(2000); // wait for all tasks to complete and threads to terminate
        assertEquals(2, customThreadPool.getWorkers().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testTaskQueue() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMaxThreads(4)
                .setIdleTime(Duration.ofMillis(1)).setName("QUEUE").build();
        assertEquals(0, customThreadPool.getWorkers().size());
        for (int i = 1; i <= 20; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertFalse(customThreadPool.getTasks().isEmpty());
        assertEquals(4, customThreadPool.getWorkers().size());
        Thread.sleep(6000); // wait for all tasks to complete
        assertEquals(0, customThreadPool.getWorkers().size());
        customThreadPool.shutdown();

        customThreadPool = CustomThreadPool.builder().setMaxThreads(0).build();
        assertEquals(0, customThreadPool.getWorkers().size());
        assertEquals(0, customThreadPool.getTasks().size());
        for (int i = 1; i <= 25; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertEquals(0, customThreadPool.getWorkers().size());
        assertEquals(25, customThreadPool.getTasks().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testShutdown() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4)
                .setIdleTime(Duration.ofSeconds(5)).setName("SHUTDOWN").build();
        assertEquals(1, customThreadPool.getWorkers().size());
        assertTrue(customThreadPool.isRunning());
        for (int i = 1; i <= 4; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertEquals(4, customThreadPool.getWorkers().size());
        Thread.sleep(2000); // wait for all tasks to complete
        assertEquals(4, customThreadPool.getWorkers().size()); // idle time has not yet elapsed
        customThreadPool.shutdown();
        assertTrue(customThreadPool.isShutdown());
        assertEquals(4, customThreadPool.getWorkers().size()); // idle time has not yet elapsed
        Thread.sleep(5000); // give the threads time to terminate
        assertEquals(0, customThreadPool.getWorkers().size(), "thread pool has been shut down");
        assertTrue(customThreadPool.isTerminated());
        assertThrows(RejectedExecutionException.class, () -> customThreadPool.submit(createRunnable(10)));
        assertEquals(0, customThreadPool.getTasks().size());
        assertEquals(0, customThreadPool.getWorkers().size());
        customThreadPool.shutdownNow();
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4)
                .setIdleTime(Duration.ofSeconds(1)).build();
        assertEquals(1, customThreadPool.getWorkers().size());
        assertTrue(customThreadPool.isRunning());
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(30));
        }
        assertEquals(4, customThreadPool.getWorkers().size());
        assertFalse(customThreadPool.getTasks().isEmpty());
        List<Runnable> unfinishedTasks = customThreadPool.shutdownNow();
        assertTrue(customThreadPool.isShutdown());
        assertEquals(0, customThreadPool.getTasks().size());
        Thread.sleep(500); // give threads enough time to terminate
        assertTrue(customThreadPool.isTerminated());
        assertEquals(0, customThreadPool.getWorkers().size(), "thread pool has terminated");
        assertFalse(unfinishedTasks.isEmpty());
        assertThrows(RejectedExecutionException.class, () -> customThreadPool.submit(createRunnable(10)));
        assertEquals(0, customThreadPool.getTasks().size());
        assertEquals(0, customThreadPool.getWorkers().size());
        customThreadPool.shutdownNow();
    }

    @Test
    void testRestart() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4)
                .setIdleTime(Duration.ofSeconds(1)).build();
        assertEquals(1, customThreadPool.getWorkers().size());
        assertTrue(customThreadPool.isRunning());
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(30));
        }
        assertEquals(4, customThreadPool.getWorkers().size());
        assertFalse(customThreadPool.getTasks().isEmpty());
        List<Runnable> unfinishedTasks = customThreadPool.shutdownNow();
        assertTrue(customThreadPool.isShutdown());
        assertEquals(0, customThreadPool.getTasks().size());
        Thread.sleep(500); // give threads enough time to terminate
        assertEquals(0, customThreadPool.getWorkers().size(), "thread pool has terminated");
        assertTrue(customThreadPool.isTerminated());
        assertFalse(unfinishedTasks.isEmpty());

        customThreadPool.start();
        assertTrue(customThreadPool.isRunning());
        assertEquals(1, customThreadPool.getWorkers().size());
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(3));
        }
        assertEquals(4, customThreadPool.getWorkers().size());
        assertFalse(customThreadPool.getTasks().isEmpty());
        customThreadPool.shutdownNow();
    }

    @Test
    void testAwaitTermination1() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().build();
        assertTrue(customThreadPool.isRunning());
        customThreadPool.shutdown(); // immediately terminate
        assertTrue(customThreadPool.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(customThreadPool.isTerminated());
    }

    @Test
    void testAwaitTermination2() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().build();
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(2));
        }
        assertTrue(customThreadPool.isRunning());
        customThreadPool.shutdown();
        assertTrue(customThreadPool.isShutdown());
        assertFalse(customThreadPool.awaitTermination(1, TimeUnit.SECONDS)); // tasks are not yet finished
        assertTrue(customThreadPool.awaitTermination(3, TimeUnit.SECONDS)); // all task should be terminated
        assertTrue(customThreadPool.isTerminated());
        assertEquals(10, customThreadPool.getCompletedTasksCount());

        customThreadPool.start();
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        customThreadPool.shutdownNow();
        assertTrue(customThreadPool.awaitTermination(10, TimeUnit.MILLISECONDS));
        assertTrue(customThreadPool.isTerminated());
        assertEquals(0, customThreadPool.getWorkers().size());
        assertTrue(10 <= customThreadPool.getCompletedTasksCount());
    }

    @Test
    void testExecute() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setMaxThreads(5)
                .setIdleTime(Duration.ofSeconds(1)).setName("EXE").build();
        assertTrue(customThreadPool.isRunning());
        assertEquals(1, customThreadPool.getWorkers().size());
        for (int i = 1; i <= 10; i++) {
            customThreadPool.execute(createRunnable(1));
        }
        assertEquals(5, customThreadPool.getWorkers().size());
        Thread.sleep(250);
        assertEquals(5, customThreadPool.getTasks().size());
        Thread.sleep(3000); // all tasks shall finish and non core threads terminate
        assertEquals(1, customThreadPool.getWorkers().size());
        assertEquals(0, customThreadPool.getTasks().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testReuseThreads() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setIdleTime(Duration.ofSeconds(5))
                .setName("REUSE").build();
        assertEquals(0, customThreadPool.getWorkers().size());
        for (int i = 1; i <= 5; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertEquals(5, customThreadPool.getWorkers().size());
        Thread.sleep(1100); // wait till all tasks have completed
        assertEquals(0, customThreadPool.getTasks().size()); // no tasks to process
        assertEquals(5, customThreadPool.getWorkers().size()); // threads not yet timed out
        for (int i = 1; i <= 5; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertEquals(5, customThreadPool.getWorkers().size());
        Thread.sleep(1100); // wait till all tasks have completed
        assertEquals(0, customThreadPool.getTasks().size()); // no tasks to process
        assertEquals(5, customThreadPool.getWorkers().size()); // threads not yet timed out
        Thread.sleep(6000); // wait so threads time out
        assertEquals(0, customThreadPool.getWorkers().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testCallable() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setMaxThreads(5)
                .setIdleTime(Duration.ofSeconds(1)).setName("CALLABLE").build();
        assertTrue(customThreadPool.isRunning());
        assertEquals(1, customThreadPool.getWorkers().size());
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            futures.add(customThreadPool.submit(createCallable(1)));
        }
        assertFalse(futures.stream().allMatch(Future::isDone));
        Thread.sleep(2500);
        assertTrue(futures.stream().allMatch(Future::isDone));

        customThreadPool.shutdownNow();
    }

    @Test
    void testCompleteCount() throws InterruptedException {
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setIdleTime(Duration.ofSeconds(1))
                .setName("COUNT").build();
        assertTrue(customThreadPool.isRunning());
        for (int i = 1; i <= 9; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        assertEquals(0, customThreadPool.getCompletedTasksCount());
        Thread.sleep(1100);
        assertEquals(9, customThreadPool.getCompletedTasksCount()); // count from alive workers
        assertEquals(9, customThreadPool.getWorkers().size());
        Thread.sleep(1100);
        assertEquals(9, customThreadPool.getCompletedTasksCount()); // count from terminated workers
        assertEquals(0, customThreadPool.getWorkers().size());

        for (int i = 1; i <= 9; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        List<Runnable> canceledTasks = customThreadPool.shutdownNow();
        // some tasks might already been picked up
        assertTrue(canceledTasks.size() <= 9);
        assertTrue(customThreadPool.awaitTermination(10, TimeUnit.MILLISECONDS));
        assertTrue(customThreadPool.isTerminated());
        // even tho all running tasks will be interrupted, the completed count goes up
        assertTrue(9 <= customThreadPool.getCompletedTasksCount());
    }

    @Test
    void testCompletedTasksCountNeverExceedsSubmittedUnderConcurrentReads() throws InterruptedException {
        // Regression test
        final int taskCount = 60;
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(0).setMaxThreads(30)
                .setIdleTime(Duration.ofMillis(20)).setName("RACE-COUNT").build();

        // Many short tasks with a tiny idle time cause rapid, continuous worker
        // churn (start/finish/terminate), maximizing the chance of hitting the
        // old race while readers poll concurrently.
        for (int i = 1; i <= taskCount; i++) {
            customThreadPool.submit(createRunnable(0));
        }

        AtomicBoolean keepReading = new AtomicBoolean(true);
        AtomicLong maxObserved = new AtomicLong(0);
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Thread reader = new Thread(() -> {
                while (keepReading.get()) {
                    long count = customThreadPool.getCompletedTasksCount();
                    maxObserved.updateAndGet(previous -> Math.max(previous, count));
                }
            });
            reader.start();
            readers.add(reader);
        }

        Thread.sleep(2000); // let all tasks complete and workers churn/terminate
        keepReading.set(false);
        for (Thread reader : readers) {
            reader.join();
        }

        assertTrue(maxObserved.get() <= taskCount, "getCompletedTasksCount() reported " + maxObserved.get()
                + " but only " + taskCount + " tasks were submitted - indicates a double-count race");
        assertEquals(taskCount, customThreadPool.getCompletedTasksCount());

        customThreadPool.shutdownNow();
    }

    @Test
    void testAwaitTerminationReturnsPromptlyOnTermination() throws InterruptedException {
        // Regression test for a missed-wakeup race
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(1).setName("PROMPT").build();
        customThreadPool.submit(createRunnable(0));
        customThreadPool.shutdown();

        long startNanos = System.nanoTime();
        boolean terminated = customThreadPool.awaitTermination(5, TimeUnit.SECONDS); // generous timeout
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(terminated);
        assertTrue(elapsedMillis < 2000, "awaitTermination() took " + elapsedMillis
                + "ms although the pool terminated almost immediately - indicates a missed wakeup");
    }

    @Test
    void testAwaitTerminationDoesNotThrowWithTinyTimeout() throws InterruptedException {
        // Regression test for a very little timeout duration.
        for (int i = 0; i < 50; i++) {
            CustomThreadPool customThreadPool = CustomThreadPool.builder().setName("TINY-" + i).build();
            customThreadPool.submit(createRunnable(0));
            customThreadPool.shutdown();
            assertDoesNotThrow(() -> customThreadPool.awaitTermination(1, TimeUnit.NANOSECONDS));
            customThreadPool.shutdownNow();
        }
    }

    @Test
    void testExecuteConcurrentWithShutdownDoesNotLoseTasks() throws InterruptedException {
        // Regression test for a race between execute() and shutdown(): a task could
        // previously be enqueued right as the pool (with no workers alive, e.g.
        // minThreads == 0) concurrently decided it was terminated, silently
        // stranding the task forever while isTerminated() reported true.
        final int iterations = 200;
        for (int i = 0; i < iterations; i++) {
            CustomThreadPool customThreadPool = CustomThreadPool.builder().setMinThreads(0).setMaxThreads(4)
                    .setIdleTime(Duration.ofMillis(50)).setName("RACE-SHUTDOWN-" + i).build();

            AtomicBoolean taskRan = new AtomicBoolean(false);
            AtomicBoolean rejected = new AtomicBoolean(false);
            Runnable task = () -> taskRan.set(true);

            Thread submitter = new Thread(() -> {
                try {
                    customThreadPool.execute(task);
                } catch (RejectedExecutionException ex) {
                    rejected.set(true);
                }
            });
            Thread shutdowner = new Thread(customThreadPool::shutdown);

            submitter.start();
            shutdowner.start();
            submitter.join();
            shutdowner.join();

            assertTrue(customThreadPool.awaitTermination(2, TimeUnit.SECONDS),
                    "pool should reach NOT_RUNNING after shutdown() once all queued/running work is drained");

            // the task must either have been rejected outright, or actually have run -
            // it must never be silently stranded in the queue while the pool reports terminated.
            if (!rejected.get()) {
                assertTrue(taskRan.get(), "task was accepted but never ran, yet the pool terminated - task was lost");
            }
            assertTrue(customThreadPool.getTasks().isEmpty(), "no task should remain queued after termination");

            customThreadPool.shutdownNow();
        }
    }

    // TODO test exception handling

    private Runnable createRunnable(final int seconds) {
        return () -> {
            try {
                Thread.sleep(1000L * seconds);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        };
    }

    private Callable<Integer> createCallable(final int seconds) {
        return () -> {
            try {
                Thread.sleep(1000L * seconds);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            return 1;
        };
    }

}

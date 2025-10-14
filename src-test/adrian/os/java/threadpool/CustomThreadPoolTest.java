package adrian.os.java.threadpool;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(2).setIdleTime(Duration.ofSeconds(1)).build();
        assertEquals(2, customThreadPool.getWorkers().size());
        Thread.sleep(2000); // Wait for idle time to expire
        assertEquals(2, customThreadPool.getWorkers().size(), "threads must not deceed minimum thread count");
        for (int i = 1; i <= 10; i++) {
            customThreadPool.submit(createRunnable(1));
        }
        Thread.sleep(350); // wait for all tasks to be picked up
        assertEquals(10, customThreadPool.getWorkers().size());
        Thread.sleep(2000); // wait for all tasks to complete and threads to terminate
        assertEquals(2, customThreadPool.getWorkers().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testTaskQueue() throws InterruptedException {
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMaxThreads(4).setIdleTime(Duration.ofMillis(1)).build();
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
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4).setIdleTime(Duration.ofSeconds(5)).build();
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
        customThreadPool.submit(createRunnable(10));
        assertEquals(0, customThreadPool.getTasks().size());
        assertEquals(0, customThreadPool.getWorkers().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testShutdownNow() throws InterruptedException {
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4).setIdleTime(Duration.ofSeconds(1)).build();
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
        customThreadPool.submit(createRunnable(10));
        assertEquals(0, customThreadPool.getTasks().size());
        assertEquals(0, customThreadPool.getWorkers().size());

        customThreadPool.shutdownNow();
    }

    @Test
    void testRestart() throws InterruptedException {
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(1).setMaxThreads(4).setIdleTime(Duration.ofSeconds(1)).build();
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
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(1).setMaxThreads(5).setIdleTime(Duration.ofSeconds(1)).build();
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
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setIdleTime(Duration.ofSeconds(5)).build();
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
        CustomThreadPool customThreadPool =
                CustomThreadPool.builder().setMinThreads(1).setMaxThreads(5).setIdleTime(Duration.ofSeconds(1)).build();
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
        CustomThreadPool customThreadPool = CustomThreadPool.builder().setIdleTime(Duration.ofSeconds(1)).build();
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

    // TODO test exception handling


    private Runnable createRunnable(final int seconds) {
        return () -> {
            try {
                Thread.sleep(1000L * seconds);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        };
    }


    private Callable<Integer> createCallable(final int seconds) {
        return () -> {
            try {
                Thread.sleep(1000L * seconds);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            return 1;
        };
    }


}

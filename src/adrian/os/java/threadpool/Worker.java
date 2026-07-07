package adrian.os.java.threadpool;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Worker to execute tasks for the {@link CustomThreadPool}.
 */
public class Worker implements Runnable {

    private final CustomThreadPool threadPool;
    private volatile boolean idle = true;
    private final boolean core;
    private final Thread thread;
    private final AtomicLong completedTasksCount = new AtomicLong(0);

    /**
     * @param customThreadPool the thread pool that manages this worker.
     * @param keepAlive true if this shall be a core worker and not terminate.
     */
    protected Worker(final CustomThreadPool customThreadPool, final boolean keepAlive) {
        this.threadPool = customThreadPool;
        this.core = keepAlive;
        this.thread = this.threadPool.getThreadFactory().newThread(this);
    }

    /**
     * get the underlying thread.
     */
    protected Thread getThread() {
        return this.thread;
    }

    /**
     * @return the thread pool that manages this worker.
     */
    protected CustomThreadPool getThreadPool() {
        return this.threadPool;
    }

    /**
     * @return the number of completed task.
     */
    protected long getCompletedTasksCount() {
        return this.completedTasksCount.get();
    }

    /**
     * @return if this is a core worker.
     */
    protected boolean isCore() {
        return this.core;
    }

    /**
     * @return true if this worker is waiting for tasks.
     */
    protected boolean isIdle() {
        return this.idle;
    }

    @Override
    public void run() {
        try {
            Runnable task;
            while (!this.thread.isInterrupted() && !this.threadPool.isTerminated() && ((task = getTask()) != null)) {
                runTask(task);
            }
        }
        finally {
            this.threadPool.stopWorker(this);
        }
    }

    private Runnable getTask() {
        Runnable task = this.threadPool.pollTask(this);
        if (task != null) {
            // flip idle here, right as the task leaves the queue,
            // otherwise countIdleWorkers() (used to size new workers in
            // performAdjustment()) can still see this worker as idle even though its
            // task has already left the queue, under-provisioning new workers by
            // exactly the number of workers caught in that window.
            this.idle = false;
        }
        return task;
    }

    private void runTask(final Runnable task) {
        try {
            task.run();
            this.completedTasksCount.incrementAndGet();
        }
        catch (Exception e) {
            handleTaskError(e);
        }
        finally {
            this.idle = true;
        }
    }

    /**
     * this handles exceptions thrown by the tasks, by delegating to this worker's thread's
     * {@link Thread.UncaughtExceptionHandler}, the same pluggable mechanism used for uncaught exceptions elsewhere.
     * Callers can customize it via the {@link CustomThreadPool}'s {@link java.util.concurrent.ThreadFactory}.
     */
    protected void handleTaskError(final Exception exception) {
        this.thread.getUncaughtExceptionHandler().uncaughtException(this.thread, exception);
    }

}

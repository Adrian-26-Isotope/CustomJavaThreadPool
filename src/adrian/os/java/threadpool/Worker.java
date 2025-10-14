package adrian.os.java.threadpool;

/**
 * Worker to execute tasks for the {@link CustomThreadPool}.
 */
public class Worker implements Runnable {

    private final CustomThreadPool threadPool;
    private volatile boolean idle = true;
    private final boolean core;
    private final Thread thread;
    private long completedTasksCount = 0;

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
     * get the underlaying thread.
     */
    protected Thread getThread() {
        return this.thread;
    }


    /**
     * @return the number of completed task.
     */
    protected long getCompletedTasksCount() {
        return this.completedTasksCount;
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
        return this.threadPool.getPollingStrategy().pollTask(this);
    }


    private void runTask(final Runnable task) {
        try {
            this.idle = false;
            task.run();
            this.completedTasksCount++;
        }
        catch (Exception e) {
            handleTaskError(e, task);
        }
        finally {
            this.idle = true;
        }
    }


    /**
     * this handles exceptions thrown by the tasks.
     */
    protected void handleTaskError(final Exception exeption, final Runnable task) {
        // TODO implement proper exception handling!
        System.err.println("Task " + task.toString() + " failed with exception: " + exeption);
    }


}

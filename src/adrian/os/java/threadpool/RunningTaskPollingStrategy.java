package adrian.os.java.threadpool;

import java.util.concurrent.TimeUnit;

/**
 * task polling behaviour for running {@link CustomThreadPool}s.
 */
public class RunningTaskPollingStrategy implements ITaskPollingStrategy {

    private final CustomThreadPool threadPool;


    /**
     * Construct a strategy for the given thread pool.
     */
    public RunningTaskPollingStrategy(final CustomThreadPool threadPool) {
        this.threadPool = threadPool;
    }


    /**
     * {@inheritDoc}<br>
     * Only non core threads can return null tasks.
     */
    @Override
    public Runnable pollTask(final Worker worker) {
        Runnable task = null;
        Thread thread = worker.getThread();
        while (!thread.isInterrupted() && this.threadPool.isRunning()) {
            try {
                task = this.threadPool.getTasks().poll(this.threadPool.getIdleTime().toNanos(), TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException _) {
                thread.interrupt();
            }
            if ((task != null) || !worker.isCore()) {
                // core workers will continue polling if task is null
                return task;
            }
        }
        return task;
    }

}

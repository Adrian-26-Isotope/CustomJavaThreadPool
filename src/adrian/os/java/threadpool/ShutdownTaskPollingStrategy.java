package adrian.os.java.threadpool;

/**
 * Task polling behaviour for {@link CustomThreadPool}s during shutdown.
 */
public class ShutdownTaskPollingStrategy implements ITaskPollingStrategy {

    private final CustomThreadPool threadPool;


    /**
     * Construct a strategy for the given thread pool.
     */
    public ShutdownTaskPollingStrategy(final CustomThreadPool threadPool) {
        this.threadPool = threadPool;
    }


    /**
     * {@inheritDoc}<br>
     * Core threads can also return null tasks, and thus be terminated as a consequence.
     */
    @Override
    public Runnable pollTask(final Worker worker) {
        return this.threadPool.getTasks().poll();
    }

}

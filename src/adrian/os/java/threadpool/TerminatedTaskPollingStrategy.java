package adrian.os.java.threadpool;

/**
 * Task polling behaviour for {@link CustomThreadPool}s after termination.
 */
public class TerminatedTaskPollingStrategy implements ITaskPollingStrategy {

    /**
     * {@inheritDoc}<br>
     * It always returns null.
     */
    @Override
    public Runnable pollTask(final Worker worker) {
        return null;
    }

}

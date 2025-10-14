package adrian.os.java.threadpool;

/**
 * this interface defines the APIs for polling tasks from the {@link CustomThreadPool}.
 */
public interface ITaskPollingStrategy {

    /**
     * The given worker polls the next task to be processed.
     *
     * @return the next task or null.
     */
    public Runnable pollTask(Worker worker);
}

package adrian.os.java.threadpool;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This thread pool implementation uses a variable amount of threads to process submitted tasks.
 */
public class CustomThreadPool extends AbstractExecutorService {

    private final int minThreads;
    private final int maxThreads;
    private final Duration idleTime;
    private final ThreadFactory threadFactory;
    private final BlockingQueue<Runnable> tasks;
    private final List<Worker> workers;
    private ThreadPoolState state;
    private ITaskPollingStrategy pollingStrategy;
    /**
     * task count of therminated workers
     */
    private final AtomicLong completedTasksCount = new AtomicLong(0);


    /**
     * @return a {@link CustomThreadPool} builder.<br>
     *         Default values are:<br>
     *         - name = JVM default<br>     
     *         - minimum threads = 0<br>
     *         - maximum threads = {@link Integer#MAX_VALUE}<br>
     *         - thread idle time = {@code Duration.ofSeconds(10)}<br>
     *         - thread factory ={@code Thread.ofVirtual().factory()}<br>
     */
    public static CustomThreadPoolBuilder builder() {
        return new CustomThreadPoolBuilder();
    }


    /**
     * Constructor
     *
     * @param minThreads the minimum amount of threads that shall not be terminated if idle.
     * @param maxThreads the maximum amount of threads created if enough tasks are submitted.
     * @param idleDuration the duration after which threads will be terminated.
     * @param threadFact a factory to define thread creation.
     */
    public CustomThreadPool(final int minThreads, final int maxThreads, final Duration idleDuration,
            final ThreadFactory threadFact) {
        if (minThreads > maxThreads) {
            throw new IllegalArgumentException("minThreads must not be greater than maxThreads");
        }
        setState(ThreadPoolState.NOT_RUNNING);
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleTime = idleDuration;
        this.threadFactory = threadFact;
        this.tasks = new LinkedBlockingQueue<>();
        this.workers = Collections.synchronizedList(new ArrayList<>(minThreads));

        start();
    }


    /**
     * @return the state of this thread pool.
     */
    protected ThreadPoolState getState() {
        return this.state;
    }


    /**
     * @return idle duration after that worker threads will be terminated.
     */
    public Duration getIdleTime() {
        return this.idleTime;
    }


    /**
     * @return the factory to create new threads.
     */
    protected ThreadFactory getThreadFactory() {
        return this.threadFactory;
    }


    /**
     * @return a current snapshot of waiting tasks.
     */
    protected BlockingQueue<Runnable> getTasks() {
        return this.tasks;
    }


    /**
     * @return a current snapshot of the worker threads.
     */
    protected List<Worker> getWorkers() {
        return this.workers;
    }


    /**
     * @return the task polling strategy of this thread pool.
     */
    protected ITaskPollingStrategy getPollingStrategy() {
        return this.pollingStrategy;
    }


    /**
     * @return number of completed tasks by this thread pool.
     */
    public long getCompletedTasksCount() {
        long count = 0;
        synchronized (this.workers) {
            for (Worker worker : this.workers) {
                count += worker.getCompletedTasksCount();
            }
        }
        return this.completedTasksCount.get() + count;
    }


    /**
     * start this thread pool, if not already started. Is automatically started at construction.
     */
    public synchronized void start() {
        if (getState() == ThreadPoolState.NOT_RUNNING) {
            setState(ThreadPoolState.RUNNING);
            for (int i = 0; i < this.minThreads; i++) {
                startWorker(true);
            }
        }
    }


    @Override
    public void execute(final Runnable command) {
        if (offer2Queue(Objects.requireNonNull(command))) {
            adjustWorkers();
        }
    }


    private boolean offer2Queue(final Runnable task) {
        if (getState() == ThreadPoolState.RUNNING) {
            return this.tasks.offer(task);
        }
        return false;
    }


    /**
     * if neccessary start new workers.
     */
    private synchronized void adjustWorkers() {
        int pendingTasks = this.tasks.size();
        int idleWorkers = countIdleWorkers();
        int missingWorkers = Math.max(0, pendingTasks - idleWorkers);
        int workersToCreate = Math.min(missingWorkers, this.maxThreads - this.workers.size());
        while (workersToCreate > 0) {
            if (this.tasks.isEmpty() || (this.maxThreads <= this.workers.size())) {
                break;
            }
            startWorker(false);
            workersToCreate--;
        }
    }


    private int countIdleWorkers() {
        int idleCount = 0;
        synchronized (this.workers) {
            for (Worker worker : this.workers) {
                if (worker.isIdle()) {
                    idleCount++;
                }
            }
        }
        return idleCount;
    }


    private Worker startWorker(final boolean keepAlive) {
        Worker worker = new Worker(this, keepAlive);
        worker.getThread().start();
        this.workers.add(worker);
        return worker;
    }


    /**
     * Terminate the give worker and remove it from the thread pool.
     */
    protected void stopWorker(final Worker worker) {
        this.completedTasksCount.addAndGet(worker.getCompletedTasksCount());

        this.workers.remove(worker);
        worker.getThread().interrupt();

        checkTermination();
    }


    private void checkTermination() {
        if (this.workers.isEmpty() && (getState() == ThreadPoolState.SHUTDOWN)) {
            setState(ThreadPoolState.NOT_RUNNING);
            synchronized (this.workers) {
                this.workers.notifyAll(); // notify termination complete
            }
        }
    }


    /**
     * set the thread pool state and polling strategy.
     */
    protected synchronized void setState(final ThreadPoolState state) {
        this.state = state;
        this.pollingStrategy = switch (state) {
            case RUNNING:
                yield new RunningTaskPollingStrategy(this);
            case SHUTDOWN:
                yield new ShutdownTaskPollingStrategy(this);
            case NOT_RUNNING:
            default:
                yield new TerminatedTaskPollingStrategy();
        };
    }


    @Override
    public void shutdown() {
        // workers will pick up remaining tasks. no new tasks can be submitted.
        if (getState() == ThreadPoolState.RUNNING) {
            setState(ThreadPoolState.SHUTDOWN);
            checkTermination();
        }
    }


    @Override
    public synchronized List<Runnable> shutdownNow() {
        List<Runnable> unfinishedTask = new ArrayList<>();
        if (getState() != ThreadPoolState.NOT_RUNNING) {
            // 1st: initiate orderly shutdown
            shutdown();

            // 2nd: cancel all pending tasks
            this.tasks.drainTo(unfinishedTask);

            // 3rd: interrupt all running threads
            synchronized (this.workers) {
                for (Worker worker : this.workers) {
                    worker.getThread().interrupt();
                }
            }

        }
        return unfinishedTask;
    }


    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        LocalDateTime start = LocalDateTime.now();
        Duration timeoutDuration = Duration.of(timeout, unit.toChronoUnit());
        LocalDateTime end = start.plus(timeoutDuration);
        while (getState() != ThreadPoolState.NOT_RUNNING) {
            if (Duration.between(start, LocalDateTime.now()).compareTo(timeoutDuration) >= 0) {
                return false;
            }
            synchronized (this.workers) {
                this.workers.wait(Duration.between(LocalDateTime.now(), end).toMillis());
            }
        }
        return true;
    }


    /**
     * Returns true if this executor is running.
     */
    public boolean isRunning() {
        return getState() == ThreadPoolState.RUNNING;
    }


    @Override
    public boolean isShutdown() {
        return getState() == ThreadPoolState.SHUTDOWN;
    }


    @Override
    public boolean isTerminated() {
        return getState() == ThreadPoolState.NOT_RUNNING;
    }


    /**
     * builder for {@link CustomThreadPool}.
     */
    public static class CustomThreadPoolBuilder {

        private String name = "";        
        private int minThreads = 0;
        private int maxThreads = Integer.MAX_VALUE;
        private Duration idleDuration = Duration.ofSeconds(10);
        private ThreadFactory threadFactory;


        /**
         * Set the name for the worker threads.<br>
         * <br>
         * Default is JVM name.
         */
        public CustomThreadPoolBuilder setName(final String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the minimum amount of threads.<br>
         * <br>
         * Default is 0.
         */
        public CustomThreadPoolBuilder setMinThreads(final int min) {
            this.minThreads = min;
            return this;
        }

        /**
         * Set the maximum amount of threads.<br>
         * <br>
         * Default is {@link Integer#MAX_VALUE}.
         */
        public CustomThreadPoolBuilder setMaxThreads(final int max) {
            this.maxThreads = max;
            return this;
        }

        /**
         * Set the idle time after its expiration a thread will be terminated.<br>
         * <br>
         * Default is 10 seconds.
         */
        public CustomThreadPoolBuilder setIdleTime(final Duration idleTime) {
            this.idleDuration = idleTime;
            return this;
        }

        /**
         * Set the factory to create new threads.<br>
         * <br>
         * {@code Thread.ofVirtual().factory()} is the default factory.
         */
        public CustomThreadPoolBuilder setThreadFactory(final ThreadFactory factory) {
            this.threadFactory = factory;
            return this;
        }

        /**
         * @return the newly constructed {@link CustomThreadPool}.
         */
        public CustomThreadPool build() {
            if (this.threadFactory == null) {
                if ((this.name != null) && !this.name.isBlank()) {
                    this.threadFactory = Thread.ofVirtual().name(this.name + "#", 0).factory();
                }
                else {
                    this.threadFactory = Thread.ofVirtual().factory();
                }
            }            
            return new CustomThreadPool(this.minThreads, this.maxThreads, this.idleDuration, this.threadFactory);
        }

    }


    /**
     * enum indicating the state of the thread pool.
     */
    @SuppressWarnings("javadoc")
    protected enum ThreadPoolState {
                                    RUNNING,
                                    NOT_RUNNING,
                                    SHUTDOWN;
    }
}

package adrian.os.java.threadpool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
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
    private volatile ThreadPoolState state;
    private WorkerAdjuster workerAdjuster;
    /**
     * task count of terminated workers
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
     * @param minThreads   the minimum amount of threads that shall not be terminated if idle. Must be greater than or
     *                     equal to 0 and less than or equal to maxThreads.
     * @param maxThreads   the maximum amount of threads created if enough tasks are submitted. Must be greater than 0
     *                     and greater than or equal to minThreads.
     * @param idleDuration the duration after which threads will be terminated. Negative durations will be treated as
     *                     {@code Duration.ZERO}.
     * @param threadFact   a factory to define thread creation.
     */
    public CustomThreadPool(final int minThreads, final int maxThreads, final Duration idleDuration,
            final ThreadFactory threadFact) {
        if (minThreads < 0 || maxThreads < 1 || minThreads > maxThreads) {
            throw new IllegalArgumentException("invalid min/max threads: min=" + minThreads + ", max=" + maxThreads);
        }
        setState(ThreadPoolState.NOT_RUNNING);
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleTime = idleDuration.isNegative() ? Duration.ZERO : idleDuration;
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
     * @return number of completed tasks by this thread pool.
     */
    public long getCompletedTasksCount() {
        // reads the accumulator inside the same critical section used to scan
        // the live workers, so the result is atomic relative to stopWorker().
        synchronized (this.workers) {
            long count = this.completedTasksCount.get();
            for (Worker worker : this.workers) {
                count += worker.getCompletedTasksCount();
            }
            return count;
        }
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
            this.workerAdjuster = new WorkerAdjuster(this);
        }
    }

    @Override
    public void execute(final Runnable command) {
        if (offer2Queue(Objects.requireNonNull(command))) {
            adjustWorkers();
        }
        else {
            throw new RejectedExecutionException("Task " + command + " rejected: thread pool is not RUNNING");
        }
    }

    private synchronized boolean offer2Queue(final Runnable task) {
        // synchronized on `this` so this check-then-act cannot interleave with
        // shutdown()/checkTermination()'s own check-then-act: a task can never be
        // enqueued after (or concurrently with) the pool declaring itself terminated.
        if (getState() == ThreadPoolState.RUNNING) {
            return this.tasks.offer(task);
        }
        return false;
    }

    /**
     * signal that worker adjustment may be necessary (e.g. after a task was enqueued). Returns immediately; the actual
     * work ({@link #performAdjustment()}) happens asynchronously on the {@link WorkerAdjuster}'s own thread, so this no
     * longer serializes concurrent {@link #execute(Runnable)} calls behind a single lock.
     */
    private void adjustWorkers() {
        this.workerAdjuster.signal();
    }

    /**
     * if necessary start new workers.<br>
     * <br>
     * Deliberately does NOT check the pool state: {@link #execute(Runnable)} always triggers this (via
     * {@link #adjustWorkers()}) right after a successful {@link #offer2Queue(Runnable)}, even during {@code SHUTDOWN},
     * so that a just-enqueued task is guaranteed to get a worker that can drain it (see
     * {@link ThreadPoolState#SHUTDOWN}). If this method is ever changed to skip starting workers while not
     * {@code RUNNING}, a task could be left stranded in {@link #tasks} with no worker left to pick it up, and
     * {@link #checkTermination()} would then never be satisfied, hanging {@link #awaitTermination(long, TimeUnit)}
     * forever.<br>
     * <br>
     * Only ever called by this pool's {@link WorkerAdjuster} thread, one call at a time, so no lock on {@code this} is
     * needed here for mutual exclusion; {@link #countIdleWorkers()} and {@link #stopWorker(Worker)} still synchronize
     * on {@link #workers} to stay safe against concurrently terminating worker threads.
     */
    protected void performAdjustment() {
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
        this.workers.add(worker);
        worker.getThread().start();
        return worker;
    }

    /**
     * Terminate the give worker and remove it from the thread pool.
     */
    protected void stopWorker(final Worker worker) {
        // a concurrent reader can never observe the worker counted in both the
        // list scan AND the accumulator, nor in neither (see getCompletedTasksCount()).
        synchronized (this.workers) {
            this.workers.remove(worker);
            this.completedTasksCount.addAndGet(worker.getCompletedTasksCount());
        }

        worker.getThread().interrupt();

        checkTermination();
    }

    private synchronized void checkTermination() {
        // tasks.isEmpty() must hold too: otherwise a task offered while RUNNING
        // (see offer2Queue()) could be stranded in the queue with no worker left
        // to pick it up, while the pool incorrectly reports itself terminated.
        // This relies on performAdjustment() always being triggered (state-independent)
        // right after a successful enqueue to actually drain that task; see the
        // warning on performAdjustment() before changing that behavior.
        if (this.workers.isEmpty() && this.tasks.isEmpty() && (getState() == ThreadPoolState.SHUTDOWN)) {
            setState(ThreadPoolState.NOT_RUNNING);
            synchronized (this.workers) {
                this.workers.notifyAll(); // notify termination complete
            }
            this.workerAdjuster.wakeup(); // let the coordinator notice termination and stop
        }
    }

    /**
     * set the thread pool state. The polling behavior for workers follows directly from
     * {@link ThreadPoolState#pollTask(Worker)}.
     */
    protected synchronized void setState(final ThreadPoolState state) {
        this.state = state;
    }

    @Override
    public synchronized void shutdown() {
        // workers will pick up remaining tasks. no new tasks can be submitted.
        // synchronized on `this`, matching offer2Queue(), so a task cannot be
        // enqueued and then "missed" by the termination check below.
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

            // draining the queue above can make tasks.isEmpty() newly true;
            // if there were no workers left to pick up those drained tasks (e.g. they
            // were never started), no worker will ever call stopWorker()->checkTermination()
            // again, so re-check here or the pool is stuck in SHUTDOWN forever.
            checkTermination();
        }
        return unfinishedTask;
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        long remainingNanos = unit.toNanos(timeout);
        synchronized (this.workers) {
            while (getState() != ThreadPoolState.NOT_RUNNING) {
                if (remainingNanos <= 0) {
                    return false;
                }
                long waitStart = System.nanoTime();
                long millis = remainingNanos / 1_000_000L;
                int nanos = (int) (remainingNanos % 1_000_000L);
                this.workers.wait(millis, nanos);
                remainingNanos -= System.nanoTime() - waitStart;
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

    protected Runnable pollTask(final Worker worker) {
        return getState().pollTask(worker);
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

}

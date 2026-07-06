package adrian.os.java.threadpool;

import java.util.concurrent.TimeUnit;

/**
 * State of a {@link CustomThreadPool}. Each constant also defines how a
 * {@link Worker} polls for its next task while the pool is in that state.
 */
enum ThreadPoolState {

    /** Active state processing tasks. */
    RUNNING {
        /**
         * {@inheritDoc}<br>
         * Only non core threads can return null tasks.
         */
        @Override
        public Runnable pollTask(final Worker worker) {
            Runnable task = null;
            Thread thread = worker.getThread();
            CustomThreadPool threadPool = worker.getThreadPool();
            while (!thread.isInterrupted() && threadPool.isRunning()) {
                try {
                    task = threadPool.getTasks().poll(threadPool.getIdleTime().toNanos(), TimeUnit.NANOSECONDS);
                } catch (InterruptedException _) {
                    thread.interrupt();
                }
                if ((task != null) || !worker.isCore()) {
                    // core workers will continue polling if task is null
                    return task;
                }
            }
            return task;
        }
    },

    /** Initial state and state after termination. */
    NOT_RUNNING {
        /**
         * {@inheritDoc}<br>
         * It always returns null.
         */
        @Override
        public Runnable pollTask(final Worker worker) {
            return null;
        }
    },

    /** Graceful shutdown in progress. */
    SHUTDOWN {
        /**
         * {@inheritDoc}<br>
         * Core threads can also return null tasks, and thus be terminated as a
         * consequence.
         */
        @Override
        public Runnable pollTask(final Worker worker) {
            return worker.getThreadPool().getTasks().poll();
        }
    };

    /**
     * The given worker polls the next task to be processed, according to this
     * state's polling behavior.
     *
     * @param worker the worker polling for its next task.
     * @return the next task or null.
     */
    public abstract Runnable pollTask(Worker worker);
}

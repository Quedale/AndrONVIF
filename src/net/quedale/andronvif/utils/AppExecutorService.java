package net.quedale.andronvif.utils;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AppExecutorService extends ThreadPoolExecutor {
    private static AppExecutorService INSTANCE;

    public static AppExecutorService getInstance(){
        if(INSTANCE == null){
            int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
            BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
            INSTANCE = new AppExecutorService(NUMBER_OF_CORES,
                    NUMBER_OF_CORES*2,
                    1,
                    TimeUnit.SECONDS,
                    taskQueue,
                    new BackgroundThreadFactory());
        }
        return INSTANCE;
    }

    private AppExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    private static class BackgroundThreadFactory implements ThreadFactory {
        private static int sTag = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("CustomThread" + sTag++);
            thread.setPriority(Thread.NORM_PRIORITY);

            // A exception handler is created to log the exception from threads
            thread.setUncaughtExceptionHandler((thread1, ex) ->
                Log.e(BackgroundThreadFactory.class.getSimpleName(), thread1.getName() + " encountered an error: " + ex.getMessage())
            );
            return thread;
        }
    }
}
package runner;

import android.app.Application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AcodeApplication extends Application {
    private static AcodeApplication instance;

    private ExecutorService executorService;
    private ExecutorService executorServiceSingle;

    public static AcodeApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        executorService = Executors.newCachedThreadPool();
        executorServiceSingle = Executors.newSingleThreadExecutor();
    }

    public ExecutorService getThreadPool() {
        return executorService;
    }

    public ExecutorService getThreadPoolSingle() {
        return executorServiceSingle;
    }

    @Override
    public void onTerminate() {
        executorService.shutdownNow();
        executorServiceSingle.shutdownNow();
        super.onTerminate();
    }
}

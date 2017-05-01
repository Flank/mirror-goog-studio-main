package android.com.java.profilertester.cpu;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class CpuAsyncTask extends AsyncTask<Integer, Void , Void> {

    private final static String TAG = CpuAsyncTask.class.getName();
    private int PERIODIC_USAGE_TASK_NUMBER = 0;
    private int FILE_TASK_NUMBER = 1;


    public final static int CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private Activity mContextReference;


    public CpuAsyncTask(Activity activity) {
        mContextReference = activity;
    }

    public static ThreadPoolExecutor getDefaultThreadPoolExecutor(int corePoolSize) {
        ThreadPoolExecutor threadPoolExecutor =  new ThreadPoolExecutor(
                corePoolSize, 128, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128), new ThreadFactory() {

            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                return new Thread(r, "CpuAsyncTask #" + mCount.getAndIncrement());
            }
        });
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        return threadPoolExecutor;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected Void doInBackground(Integer... parameters) {
        int taskNumber = parameters[0];

        if (taskNumber == PERIODIC_USAGE_TASK_NUMBER) {
            new PeriodicRunningTask().execute();
        }
        if (taskNumber == FILE_TASK_NUMBER) {
            new FileWritingTask(mContextReference).execute();
        }

        return null;
    }
}

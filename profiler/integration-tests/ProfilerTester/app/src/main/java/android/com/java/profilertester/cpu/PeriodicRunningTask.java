package android.com.java.profilertester.cpu;

import android.os.AsyncTask;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class PeriodicRunningTask {
    private static int ITERATION_COUNT = 5;
    private static int PERIOD_TIME = 2;

    public void execute() {
        ThreadPoolExecutor lastThreadPoolExecutor = null;
        ThreadPoolExecutor threadPoolExecutor = null;
        try {
            for (int i = 0; i < ITERATION_COUNT; ++i) {
                int singleTaskNumber = Math.max(1, CpuAsyncTask.CORE_COUNT - 1);
                threadPoolExecutor = CpuAsyncTask.getDefaultThreadPoolExecutor(singleTaskNumber);

                for (int k = 0; k < singleTaskNumber; ++k) {
                    new SingleCoreRunningTask().executeOnExecutor(threadPoolExecutor, PERIOD_TIME);
                }

                TimeUnit.SECONDS.sleep(PERIOD_TIME);
                if (lastThreadPoolExecutor != null) {
                    lastThreadPoolExecutor.shutdown();
                }
                lastThreadPoolExecutor = threadPoolExecutor;
                TimeUnit.SECONDS.sleep(PERIOD_TIME);
            }

            TimeUnit.SECONDS.sleep(2);
            if (lastThreadPoolExecutor != null) {
                lastThreadPoolExecutor.shutdown();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    class SingleCoreRunningTask extends AsyncTask<Integer, Void, Void> {
        void meaninglessRunning() {
            double value = Math.E;
            for (int i = 0; i < 10000; ++i) {
                value += Math.sin(value) + Math.cos(value);
            }
        }

        @Override
        protected Void doInBackground(Integer... paras) {
            long stopTime = System.currentTimeMillis() + paras[0] * 1000;
            while (System.currentTimeMillis() < stopTime) {
                meaninglessRunning();
            }
            return null;
        }
    }
}

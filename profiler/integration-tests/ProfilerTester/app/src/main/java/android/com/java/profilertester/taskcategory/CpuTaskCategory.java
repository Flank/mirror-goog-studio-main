package android.com.java.profilertester.taskcategory;

import android.com.java.profilertester.util.Lookup3;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CpuTaskCategory extends TaskCategory {
    private final static int CORE_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int RUNNING_TIME_S = 20;

    private final List<Task> mTasks;

    static {
        System.loadLibrary("native_cpu");
    }

    public native int fib(int index);

    public CpuTaskCategory(@NonNull File filesDir) {
        mTasks =
                Arrays.asList(
                        new PeriodicRunningTask(),
                        new FileWritingTask(filesDir),
                        new MaximumPowerTask(new SingleThreadIntegerTask(RUNNING_TIME_S)),
                        new MaximumPowerTask(new SingleThreadFpuTask(RUNNING_TIME_S)),
                        new MaximumPowerTask(new SingleThreadMemoryTask(RUNNING_TIME_S)),
                        new RunNativeCodeTask());
    }

    private static ThreadPoolExecutor getDefaultThreadPoolExecutor(int corePoolSize) {
        ThreadPoolExecutor threadPoolExecutor =  new ThreadPoolExecutor(
                corePoolSize, 128, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(128), new ThreadFactory() {

            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, "CpuAsyncTask #" + mCount.getAndIncrement());
            }
        });
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        return threadPoolExecutor;
    }

    @NonNull
    @Override
    public List<? extends Task> getTasks() {
        return mTasks;
    }

    @NonNull
    @Override
    protected String getCategoryName() {
        return "CPU";
    }

    private static class FileWritingTask extends Task {
        private static String FOLDER_NAME = "test_folder";
        private static String FILE_NAME = "test_file";
        static final int THREAD_COUNT = 4;
        private File mFilesDir;

        private FileWritingTask(@NonNull File filesDir) {
            mFilesDir = filesDir;
        }

        @Nullable
        public String execute() {
            File dir = new File(mFilesDir.getAbsolutePath() + FOLDER_NAME);
            dir.mkdirs();
            File file = new File(dir, FILE_NAME);
            file.delete();
            ThreadPoolExecutor thread_pool_executor = getDefaultThreadPoolExecutor(THREAD_COUNT);


            // create WRITING_THREAD_NUMBER threads writing concurrently
            SingleCoreFileTask[] task = new SingleCoreFileTask[THREAD_COUNT];
            for (int k = 0; k < THREAD_COUNT; ++k) {
                task[k] = new SingleCoreFileTask(file);
                task[k].executeOnExecutor(thread_pool_executor, SingleCoreFileTask.WRITING, k);
            }

            // wait for writing threads to stop
            for (int k = 0; k < THREAD_COUNT; ++k) {
                try {
                    task[k].get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }

            // wait for another 2 seconds
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }

            thread_pool_executor = getDefaultThreadPoolExecutor(THREAD_COUNT);

            // create READING_THREAD_NUMBER threads reading concurrently
            task = new SingleCoreFileTask[THREAD_COUNT];
            for (int k = 0; k < THREAD_COUNT; ++k) {
                task[k] = new SingleCoreFileTask(file);
                task[k].executeOnExecutor(thread_pool_executor, SingleCoreFileTask.READING, k);
            }

            // wait for writing threads to stop
            for (int k = 0; k < THREAD_COUNT; ++k) {
                try {
                    task[k].get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }

            // remove the file
            file.delete();
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "File Read and Write";
        }

        private static class SingleCoreFileTask extends AsyncTask<Integer, Void, Void> {
            private static final int READING = 0;
            private static final int WRITING = 1;
            private static final int BUFFER_SIZE = (1 << 22);
            private static final int BUFFER_COUNT = 100;
            private static final int FILE_SIZE = BUFFER_SIZE * BUFFER_COUNT;
            private File mFile;

            private SingleCoreFileTask(File file) {
                mFile = file;
            }

            private void writeFile(int number) {
                try {
                    RandomAccessFile randomAccessFile = new RandomAccessFile(mFile, "rw");

                    byte[] byteArray = new byte[BUFFER_SIZE];
                    Arrays.fill(byteArray, (byte) -1);
                    randomAccessFile.seek(number * FILE_SIZE);
                    for (int k = 0; k < BUFFER_COUNT; ++k) {
                        randomAccessFile.write(byteArray);
                    }
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            private void readFile(int number) {
                try {
                    RandomAccessFile randomAccessFile = new RandomAccessFile(mFile, "r");
                    randomAccessFile.seek(number * FILE_SIZE);
                    byte[] array = new byte[BUFFER_SIZE];
                    for (int k = 0; k < BUFFER_COUNT; ++k) {
                        randomAccessFile.readFully(array);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            protected Void doInBackground(Integer... args) {
                int parameter = args[0];
                if (parameter == READING) {
                    readFile(args[1]);
                }
                if (parameter == WRITING) {
                    writeFile(args[1]);
                }
                return null;
            }
        }
    }

    public static class PeriodicRunningTask extends Task {
        private static int ITERATION_COUNT = 5;
        private static int PERIOD_TIME_S = 2;

        @Nullable
        public String execute() {
            ThreadPoolExecutor lastThreadPoolExecutor = null;
            ThreadPoolExecutor threadPoolExecutor;
            try {
                for (int i = 0; i < ITERATION_COUNT; ++i) {
                    int singleTaskNumber = Math.max(1, CORE_COUNT - 1);
                    threadPoolExecutor = getDefaultThreadPoolExecutor(singleTaskNumber);

                    for (int k = 0; k < singleTaskNumber; ++k) {
                        threadPoolExecutor.execute(new SingleThreadFpuTask(PERIOD_TIME_S));
                    }

                    TimeUnit.SECONDS.sleep(PERIOD_TIME_S);
                    if (lastThreadPoolExecutor != null) {
                        lastThreadPoolExecutor.shutdown();
                    }
                    lastThreadPoolExecutor = threadPoolExecutor;
                    TimeUnit.SECONDS.sleep(PERIOD_TIME_S);
                }

                TimeUnit.SECONDS.sleep(2);
                if (lastThreadPoolExecutor != null) {
                    lastThreadPoolExecutor.shutdown();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return e.toString();
            }
            return null;
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Periodic Usage";
        }
    }

    private static class MaximumPowerTask extends Task {
        private static final int NUM_CORES = Runtime.getRuntime().availableProcessors();
        @NonNull private final ComputationTask mComputationTask;

        private MaximumPowerTask(@NonNull ComputationTask computationTask) {
            mComputationTask = computationTask;
        }

        @Nullable
        @Override
        protected String execute() throws Exception {
            ThreadPoolExecutor executor = getDefaultThreadPoolExecutor(NUM_CORES);
            List<Future<?>> futures = new ArrayList<>(NUM_CORES);
            for (int i = 0; i < NUM_CORES; i++) {
                futures.add(executor.submit(mComputationTask));
            }
            for (Future<?> future : futures) {
                future.get();
            }
            return "Computation finished.";
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return mComputationTask.getTaskDescription();
        }
    }

    private abstract static class ComputationTask implements Runnable {
        protected static final int NUM_ITERATIONS = 10000;
        private final int mSecondsToRun;

        protected ComputationTask(int secondsToRun) {
            mSecondsToRun = secondsToRun;
        }

        protected abstract void doComputation();

        @NonNull
        protected abstract String getTaskDescription();

        @Override
        public void run() {
            long stopTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(mSecondsToRun);
            while (System.currentTimeMillis() < stopTime) {
                doComputation();
            }
        }
    }

    private static final class SingleThreadFpuTask extends ComputationTask {
        private SingleThreadFpuTask(int secondsToRun) {
            super(secondsToRun);
        }

        @Override
        protected void doComputation() {
            double value = Math.E;
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                value += Math.sin(value) + Math.cos(value);
            }
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Full Power FPU Task";
        }
    }

    private static final class SingleThreadIntegerTask extends ComputationTask {
        private final int[] mValues;
        private int mSeed = 0;

        private SingleThreadIntegerTask(int secondsToRun) {
            super(secondsToRun);
            mValues = new int[NUM_ITERATIONS];
            for (int i = 0; i < mValues.length; i++) {
                mValues[i] = i;
            }
        }

        @Override
        protected void doComputation() {
            mSeed = Lookup3.hashwords(mValues, mSeed);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Full Power Integer Task";
        }
    }

    private static final class SingleThreadMemoryTask extends ComputationTask {
        private static final int INT_COUNT = 4 * 1024 * 1024;
        private final int[] mValues;
        private int mIndex = 0;

        private SingleThreadMemoryTask(int secondsToRun) {
            super(secondsToRun);

            if ((INT_COUNT & (INT_COUNT - 1)) > 0) {
                throw new RuntimeException("INT_COUNT needs to be power of 2!");
            }

            int size = INT_COUNT;
            mValues = new int[size];
            // Most of the non-power of 2 numbers hardcoded are just random primes.
            for (int i = 0; i < mValues.length; i++, size += 17) {
                mValues[i] = size; // Start with something bigger.
            }
        }

        @Override
        protected void doComputation() {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                int newValue = stupidHash(mValues[mIndex], mIndex);
                mValues[mIndex] = newValue;
                mIndex = newValue & (INT_COUNT - 1);
            }
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Full Power Memory Pressure Task";
        }

        private int stupidHash(int input, int offset) {
            int s1 = (input * 103 + offset) % 65521;
            return s1 * 21179 + s1;
        }
    }

    public class RunNativeCodeTask extends Task {
        private static final int FIB_INDEX = 40;

        @Nullable
        public String execute() {
            int result = CpuTaskCategory.this.fib(FIB_INDEX);
            return String.format(
                    Locale.getDefault(),
                    "Calling native method fib(%d), returned %d",
                    FIB_INDEX,
                    result);
        }

        @NonNull
        @Override
        protected String getTaskDescription() {
            return "Run Native Code Task";
        }
    }
}

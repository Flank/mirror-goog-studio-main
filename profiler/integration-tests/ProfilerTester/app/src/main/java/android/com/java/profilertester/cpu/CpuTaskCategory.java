package android.com.java.profilertester.cpu;

import android.com.java.profilertester.profiletask.TaskCategory;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CpuTaskCategory extends TaskCategory {
    private final static int CORE_COUNT = Runtime.getRuntime().availableProcessors();

    private final List<Task> mTasks;

    public CpuTaskCategory(@NonNull File filesDir) {
        mTasks = Arrays.asList(new PeriodicRunningTask(), new FileWritingTask(filesDir));
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
        protected String getTaskName() {
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
        private static int PERIOD_TIME = 2;

        @Nullable
        public String execute() {
            ThreadPoolExecutor lastThreadPoolExecutor = null;
            ThreadPoolExecutor threadPoolExecutor;
            try {
                for (int i = 0; i < ITERATION_COUNT; ++i) {
                    int singleTaskNumber = Math.max(1, CORE_COUNT - 1);
                    threadPoolExecutor = getDefaultThreadPoolExecutor(singleTaskNumber);

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
                return e.toString();
            }
            return null;
        }

        @NonNull
        @Override
        protected String getTaskName() {
            return "Periodic Usage";
        }

        private static class SingleCoreRunningTask extends AsyncTask<Integer, Void, Void> {
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
}

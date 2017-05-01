package android.com.java.profilertester.cpu;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileWritingTask {
    private static String FOLDER_NAME = "test_folder";
    private static String FILE_NAME = "test_file";
    static final int THREAD_COUNT = 4;
    private Activity mContextReference;

    public FileWritingTask(Activity activity) {
        mContextReference = activity;
    }

    public void execute() {
        File root =  mContextReference.getFilesDir();
        File dir = new File(root.getAbsolutePath() + FOLDER_NAME);
        dir.mkdirs();
        File file = new File(dir, FILE_NAME);
        file.delete();
        ThreadPoolExecutor thread_pool_executor = CpuAsyncTask.getDefaultThreadPoolExecutor(THREAD_COUNT);


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
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        // wait for another 2 seconds
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        thread_pool_executor = CpuAsyncTask.getDefaultThreadPoolExecutor(THREAD_COUNT);

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
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        // remove the file
        file.delete();
    }

    class SingleCoreFileTask extends AsyncTask<Integer, Void, Void> {
        public static final int READING = 0;
        public static final int WRITING = 1;
        private static final int BUFFER_SIZE = (1 << 22);
        private static final int BUFFER_COUNT = 100;
        private static final int FILE_SIZE = BUFFER_SIZE * BUFFER_COUNT;
        private File mFile;

        public SingleCoreFileTask(File file) {
            mFile = file;
        }

        private void writeFile(int number) {
            try {
                RandomAccessFile randomAccessFile = new RandomAccessFile(mFile, "rw");

                byte[] byteArray = new byte[BUFFER_SIZE];
                Arrays.fill(byteArray, (byte)-1);
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
